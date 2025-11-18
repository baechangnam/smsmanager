package apps.kr.smsmanager.sync;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class NotificationDismissReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("ACTION_RESTART_SYNC_FOREGROUND".equals(intent.getAction())) {
            Log.d("SyncService", "Notification dismissed, restarting foreground service");

            Intent serviceIntent = new Intent(context, SyncService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
