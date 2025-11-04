package apps.kr.smsmanager.sms;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import apps.kr.smsmanager.common.NotificationHelper;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;
import apps.kr.smsmanager.db.MessageDao;

public class SmsDeliverReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsDeliverReceiver";
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();




    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        boolean isDeliver = "android.provider.Telephony.SMS_DELIVER".equals(action);
        boolean isReceived = "android.provider.Telephony.SMS_RECEIVED".equals(action);

        if (!isDeliver && !isReceived) return;

        // RECEIVED는 백업용
        if (isReceived) {
            Log.d(TAG, "ignore SMS_RECEIVED (we handle DELIVER only)");
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "extras == null");
            return;
        }

        Object[] pdus = (Object[]) extras.get("pdus");
        String format = extras.getString("format");
        Log.d(TAG, "pdus=" + (pdus == null ? "null" : pdus.length) + " format=" + format);
        if (pdus == null || pdus.length == 0) return;

        StringBuilder body = new StringBuilder();
        String from = null;
        for (Object pdu : pdus) {
            SmsMessage msg = (format != null)
                    ? SmsMessage.createFromPdu((byte[]) pdu, format)
                    : SmsMessage.createFromPdu((byte[]) pdu);
            if (from == null) from = msg.getDisplayOriginatingAddress();
            body.append(msg.getMessageBody());
        }
        String bodyStr = body.toString();
        long now = System.currentTimeMillis();

        Log.i(TAG, "SMS from=" + from + ", body=" + bodyStr);

        // === 기본 SMS 앱인지 판정 ===
        boolean isDefault = false;
        String def = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            def = Telephony.Sms.getDefaultSmsPackage(context);
        }
        Log.d(TAG, "defaultSms=" + def + ", mine=" + context.getPackageName());

        if (def == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = context.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                    isDefault = true;
                    Log.d(TAG, "ROLE_SMS is held -> treat as default sms app");
                }
            }
        } else {
            isDefault = def.equals(context.getPackageName());
        }

        // 시스템에 실제로 들어간 uri
        Uri inserted = null;

        if (isDefault) {
            ContentValues cv = new ContentValues();
            cv.put(Telephony.Sms.ADDRESS, from);
            cv.put(Telephony.Sms.BODY, bodyStr);
            cv.put(Telephony.Sms.DATE, now);
            cv.put(Telephony.Sms.READ, 0);
            cv.put(Telephony.Sms.SEEN, 0);
            cv.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);

            try {
                inserted = context.getContentResolver()
                        .insert(Telephony.Sms.Inbox.CONTENT_URI, cv);
                Log.d(TAG, "inserted to inbox -> " + inserted);
            } catch (Exception e) {
                Log.e(TAG, "insert to inbox failed", e);
            }

            if (inserted == null) {
                try {
                    inserted = context.getContentResolver()
                            .insert(Telephony.Sms.CONTENT_URI, cv);
                    Log.d(TAG, "inserted to sms -> " + inserted);
                } catch (Exception e2) {
                    Log.e(TAG, "insert to sms failed", e2);
                }
            }
        } else {
            Log.w(TAG, "we are NOT default (or device returned null w/o role) -> skip system insert");
        }

        long systemId = 0L;
        if (inserted != null) {
            try { systemId = Long.parseLong(inserted.getLastPathSegment()); } catch (Exception ignore) {}
        }

// ❌ 임시 now 키 사용 금지
        if (systemId == 0L) {
            Log.w(TAG, "Unknown systemId - skip local DB insert to avoid duplicates");
            // UI 토스트/노티만 하고 return
            return;
        }

        // 알림은 바로
        NotificationHelper.showIncomingSms(
                context.getApplicationContext(),
                from,
                bodyStr
        );

        // 👇 이 context는 브로드캐스트 context라서
        // 나중에 다른 스레드에서 쓸 땐 앱 컨텍스트로 바꿔놓자
        final Context appCtx = context.getApplicationContext();
        final long finalSystemId = systemId;
        final String finalFrom = from;
        final String finalBodyStr = bodyStr;
        final long finalNow = now;

        final PendingResult pr = goAsync();
        EXECUTOR.execute(() -> {
            try {
                AppDatabase db = AppDatabase.get(appCtx);
                MessageDao dao = db.messageDao();

                LocalMessage lm = new LocalMessage();
                lm.sysId = finalSystemId;
                lm.isMms = false;
                lm.threadId = 0;
                lm.address = finalFrom;
                lm.body = finalBodyStr;
                lm.date = finalNow;
                lm.box = 1;
                lm.uploaded = false;

                dao.upsert(lm);


                // ✅ 앱 내부로만 딱 날린다
                Intent ui = new Intent("apps.kr.smsmanager.SMS_RECEIVED_INTERNAL");
                ui.setPackage(appCtx.getPackageName());              // ← 이게 핵심
                ui.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);        // ← 즉시 처리 유도
                ui.putExtra("from", finalFrom);
                ui.putExtra("body", finalBodyStr);
                appCtx.sendBroadcast(ui);

                Log.d(TAG, "internal broadcast sent");

            } catch (Exception e) {
                Log.e(TAG, "local db insert failed", e);
            } finally {
                pr.finish();
            }
        });
    }
}
