package apps.kr.smsmanager.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import apps.kr.smsmanager.common.MmsUtils;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;
import apps.kr.smsmanager.db.MessageDao;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmsSyncManager {

    private static final String TAG = "SmsSyncManager";

    private static final String PREF = "sms_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_SERVER_ON = "server_on";
    private static final String KEY_LAST_SYNC_TS = "last_sync_ts";

    private static final String DEFAULT_SERVER_URL = "https://192.168.0.10/sms/upload";

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private static OkHttpClient client = new OkHttpClient();

    private static long sLastRealRun = 0L;

    public static void syncNow(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - sLastRealRun < 10_000L) {
            // 너무 자주 호출되면 그냥 무시
            return;
        }
        sLastRealRun = now;

        // 0) 네트워크 없으면 바로 스킵
        if (!isNetworkAvailable(ctx)) {
            return;
        }

        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            boolean on = sp.getBoolean(KEY_SERVER_ON, true);

            if (!on) {

                return;
            }

            String url = sp.getString(KEY_SERVER_URL, null);


            if (url == null || url.trim().isEmpty()) {
                url = DEFAULT_SERVER_URL;
                sp.edit().putString(KEY_SERVER_URL, url).apply();

            }



            // 1) DB에서 최근 30건 조회
            AppDatabase db = AppDatabase.get(ctx.getApplicationContext());
            MessageDao dao = db.messageDao();
            List<LocalMessage> latest = dao.getLatest(30);
            if (latest == null || latest.isEmpty()) {

                return;
            }

            long lastSyncTs = sp.getLong(KEY_LAST_SYNC_TS, 0L);

            List<LocalMessage> toSend = new ArrayList<>();
            long maxTs = lastSyncTs;

            for (LocalMessage m : latest) {
                if (m.date > lastSyncTs) {
                    toSend.add(m);
                    if (m.date > maxTs) maxTs = m.date;
                }
            }

            if (toSend.isEmpty()) {

                return;
            }

            // 2) JSON 만들기
            String deviceNumber = MmsUtils.getDevicePhoneNumber(ctx);


            JSONArray arr = new JSONArray();
            for (LocalMessage m : toSend) {
                JSONObject o = new JSONObject();
                o.put("device_phone_number", deviceNumber);
                o.put("from_number", m.address == null ? "" : m.address);
                o.put("timestamp", m.date);
                o.put("message_body", m.body == null ? "" : m.body);
                arr.put(o);
            }

            String json = arr.toString();


            // 3) HTTP POST
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                if (resp.isSuccessful()) {
                    sp.edit().putLong(KEY_LAST_SYNC_TS, maxTs).apply();
                    Log.d(TAG, "sync success, lastSyncTs updated to " + maxTs);
                } else {
                    Log.w(TAG, "upload failed: " + resp.code() + " / " + resp.message());
                }
            }

        } catch (Exception e) {
            // 여기서 한 번만 조용히 처리 → 서비스 안 죽고, 스택트레이스도 안 넘김
            Log.w(TAG, "sync error: " + e.getClass().getSimpleName() + " / " + e.getMessage());
        }
    }

    // SplashActivity 에 있던 거랑 거의 동일
    private static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }
}

