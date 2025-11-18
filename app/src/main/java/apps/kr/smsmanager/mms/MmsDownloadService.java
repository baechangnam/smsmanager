// MmsDownloadService.java
package apps.kr.smsmanager.mms;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ProxyInfo;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import apps.kr.smsmanager.common.MmsUtils;
import apps.kr.smsmanager.common.NotificationHelper;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;

public class MmsDownloadService extends IntentService {

    private static final String TAG = "MmsDownloadService";

    public MmsDownloadService() { super("MmsDownloadService"); }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String contentLocation = intent.getStringExtra("contentLocation");
        final String transactionId   = intent.getStringExtra("transactionId");
        if (contentLocation == null) {
            Log.w(TAG, "no contentLocation");
            return;
        }

        // 0) ✅ 중복 방지
        if (isDuplicate(transactionId, contentLocation)) {
            Log.d(TAG, "dup mms skip: tx=" + transactionId + ", ct=" + contentLocation);
            return;
        }

        try {
            // 1) MMSC에서 PDU 받기
            byte[] pduData = httpGetPdu(contentLocation);
            if (pduData == null) {
                Log.w(TAG, "download pdu fail");
                return;
            }

            // 2) retrieve_conf 파싱
            apps.kr.smsmanager.mms.pdu.GenericPdu pdu =
                    new apps.kr.smsmanager.mms.pdu.PduParser(pduData).parse();
            if (pdu == null ||
                    pdu.getMessageType() != apps.kr.smsmanager.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF) {
                Log.w(TAG, "not retrieve_conf");
                return;
            }
            apps.kr.smsmanager.mms.pdu.RetrieveConf rc =
                    (apps.kr.smsmanager.mms.pdu.RetrieveConf) pdu;

            // 3) ContentProvider 에 저장
            long newId = persistToInbox(this, rc);
            Uri  newUri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, String.valueOf(newId));
            Log.i(TAG, "mms persisted id=" + newId);

            // 4) ✅ READ/SEEN 플래그(정책에 맞게)
            setSeenReadFlags(newUri, /*seen=*/0, /*read=*/0);

            // 5) ✅ 같은 content-location 가진 130 알림 정리
            cleanupNotificationIndByCtL(contentLocation);

            // 6) ✅ thread_id 재조회
            long threadId = queryLong(newUri, "thread_id", 0L);

// 6-1) ✅ date 재조회 (Telephony.Mms.date는 "초" 단위라서 *1000 필요)
            long mmsSec = queryLong(newUri, "date", System.currentTimeMillis() / 1000L);
            long mmsDate = mmsSec * 1000L;

// 7) 텍스트/주소 추출
            String text = MmsUtils.readMmsText(this, newId);
            String from = MmsUtils.readMmsAddress(this, newId);
            if (text == null || text.isEmpty()) text = "[MMS]";

// 8) ✅ 우리 로컬 DB 저장
            LocalMessage lm = new LocalMessage();
            lm.sysId    = newId;
            lm.isMms    = true;
            lm.threadId = threadId;
            lm.address  = from;
            lm.body     = text;
            lm.date     = mmsDate;     // ✅ 시스템 date 기준으로 통일
            lm.box      = 1;
            lm.uploaded = false;
            AppDatabase.get(getApplicationContext()).messageDao().upsert(lm);
            NotificationHelper.showIncomingMms(
                    getApplicationContext(),
                    from,
                    text,
                    newId,      // 고유 알림 ID로 쓰기 좋음
                    threadId    // (선택) groupKey 등에 활용 가능
            );


            // 9) ✅ UI 브로드캐스트
            Intent ui = new Intent("apps.kr.smsmanager.MMS_RECEIVED_INTERNAL");
            ui.setPackage(getPackageName());
            ui.putExtra("mms_id", newId);
            ui.putExtra("from", from);
            ui.putExtra("body", text);
            ui.putExtra("thread_id", threadId);
            sendBroadcast(ui);

            // 10) ✅ (선택) NotifyResp.ind 전송
            sendNotifyResp(transactionId, contentLocation);

        } catch (Exception e) {
            Log.e(TAG, "download/persist mms fail", e);
        }
    }

    // ----------------- helpers -----------------

    private byte[] httpGetPdu(String urlStr) throws Exception {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        MmsNetworkHelper helper =
                new MmsNetworkHelper(this);

        Network mmsNet = null;
        HttpURLConnection conn = null;
        try {
            // 1) MMS 네트워크 확보
            mmsNet = helper.acquire(15_000);
            if (mmsNet == null) throw new IOException("No MMS network");

            // 2) 프로세스 바인딩
           MmsNetworkHelper.bindProcessTo(cm, mmsNet);

            // 3) 프록시 확인 (★ 핵심)
            LinkProperties lp = cm.getLinkProperties(mmsNet);
            ProxyInfo pinfo = (lp != null) ? lp.getHttpProxy() : null;

            URL url = new URL(urlStr);
            if (pinfo != null && pinfo.getHost() != null && pinfo.getPort() > 0) {
                // 통신사가 요구하는 HTTP 프록시
                java.net.Proxy jProxy = new java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(pinfo.getHost(), pinfo.getPort())
                );
                // 바인딩된 네트워크 + 프록시 조합
                conn = (HttpURLConnection) url.openConnection(jProxy);
            } else {
                // 프록시가 없으면 바인딩 네트워크로 직접 연결
                conn = (HttpURLConnection) mmsNet.openConnection(url);
            }

            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            // 권장 헤더
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", "Android-Mms/5.0");
            conn.setRequestProperty("Connection", "close");

            conn.connect();

            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}
            // 4) 바인딩 해제
            MmsNetworkHelper.unbindProcess(cm);
            helper.release(); // ★ 다운로드/퍼시스트 끝난 뒤에 해제
        }
    }


    private long persistToInbox(Context ctx, apps.kr.smsmanager.mms.pdu.RetrieveConf rc) throws Exception {
        apps.kr.smsmanager.mms.pdu.PduPersister persister =
                apps.kr.smsmanager.mms.pdu.PduPersister.getPduPersister(ctx);
        Uri uri = persister.persist(
                rc,
                Telephony.Mms.Inbox.CONTENT_URI,
                true,   // createThreadId
                true,   // groupMmsEnabled
                null
        );
        return Long.parseLong(uri.getLastPathSegment());
    }

    // ✅ READ/SEEN 세팅
    private void setSeenReadFlags(Uri msgUri, int seen, int read) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(Telephony.Mms.SEEN, seen);
            cv.put(Telephony.Mms.READ, read);
            getContentResolver().update(msgUri, cv, null, null);
        } catch (Throwable t) {
            Log.w(TAG, "setSeenReadFlags fail", t);
        }
    }

    // ✅ 같은 ct_l 가진 130(notification_ind) 정리 (삭제가 부담되면 READ/SEEN만 1로)
    private void cleanupNotificationIndByCtL(String contentLocation) {
        if (contentLocation == null) return;
        try {
            String where = "m_type = 130 AND ct_l = ?";
            String[] args = { contentLocation };
            int del = getContentResolver().delete(Telephony.Mms.CONTENT_URI, where, args);
            Log.d(TAG, "cleanup notif_ind by ct_l, deleted=" + del);
        } catch (Throwable t) {
            Log.w(TAG, "cleanup 130 fail", t);
        }
    }

    // ✅ thread_id 조회 등 단일 long 컬럼 읽기
    private long queryLong(Uri uri, String col, long defVal) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{col}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Throwable t) {
            Log.w(TAG, "queryLong fail " + col, t);
        } finally {
            if (c != null) c.close();
        }
        return defVal;
    }

    // ✅ 중복방지: txId 우선, 없으면 ct_l로
    private boolean isDuplicate(String txId, String ctLoc) {
        SharedPreferences sp = getSharedPreferences("mms_dl", MODE_PRIVATE);
        if (txId != null) {
            String k = "tx:" + txId;
            if (sp.getBoolean(k, false)) return true;
            sp.edit().putBoolean(k, true).apply();
            return false;
        }
        if (ctLoc != null) {
            String k = "ct:" + ctLoc;
            if (sp.getBoolean(k, false)) return true;
            sp.edit().putBoolean(k, true).apply();
            return false;
        }
        return false;
    }

    // ✅ (선택) 수신확인 응답 전송 — 테스트 용 간이 구현
    private void sendNotifyResp(String transactionId, String contentLocation) {
        if (transactionId == null || contentLocation == null) return;
        try {
            apps.kr.smsmanager.mms.pdu.NotifyRespInd n =
                    new apps.kr.smsmanager.mms.pdu.NotifyRespInd(
                            apps.kr.smsmanager.mms.pdu.PduHeaders.CURRENT_MMS_VERSION,
                            transactionId.getBytes(),
                            apps.kr.smsmanager.mms.pdu.PduHeaders.STATUS_RETRIEVED
                    );
            byte[] respPdu = new apps.kr.smsmanager.mms.pdu.PduComposer(this, n).make();
            if (respPdu == null) return;

            HttpURLConnection conn = (HttpURLConnection) new URL(contentLocation).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/vnd.wap.mms-message");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(respPdu);
            }
            int code = conn.getResponseCode();
            Log.d(TAG, "notify-resp http " + code);
        } catch (Throwable t) {
            Log.w(TAG, "sendNotifyResp fail", t);
        }
    }
}
