package apps.kr.smsmanager.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREF = "sms_prefs";
    private static final String KEY_SERVER_ON = "server_on";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            SharedPreferences sp =
                    context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            boolean on = sp.getBoolean(KEY_SERVER_ON, true);

            // 1) 서버 ON이면 포그라운드 서비스 재시작
            if (on) {
                Intent svc = new Intent(context, SyncService.class);
                ContextCompat.startForegroundService(context, svc);
            }

            // 2) WorkManager 주기 작업도 등록 (백업용)
            SmsSyncWorker.schedulePeriodic(context);
        }
    }
}
