package apps.kr.smsmanager.sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Telephony;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.mms.MmsObserver;

public class SyncService extends Service {

    public static final String CHANNEL_ID = "sms_sync_channel";

    private ScheduledExecutorService scheduler;

    private static boolean sMmsObserverRegistered = false;


    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();

        // MMS ContentObserver용 백그라운드 쓰레드
        if (!sMmsObserverRegistered) {
            HandlerThread ht = new HandlerThread("MmsObserverThread");
            ht.start();
            Handler bgHandler = new Handler(ht.getLooper());

            MmsObserver observer = new MmsObserver(getApplicationContext(), bgHandler);
            getContentResolver().registerContentObserver(
                    Telephony.Mms.CONTENT_URI,
                    true,
                    observer
            );
            sMmsObserverRegistered = true;
        }

        // 포그라운드 서비스 시작
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("문자 동기화 실행 중")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        startForeground(1001, notification);

        // 10초 주기 동기화 타이머
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::syncTick, 0, 10, TimeUnit.SECONDS);
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS 동기화",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("주기적으로 서버로 문자 내용을 동기화");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void syncTick() {
        SmsSyncManager.syncNow(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 강제 종료 후에도 OS가 적당히 다시 살리도록
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
