package apps.kr.smsmanager.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class SmsSyncWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "sms_sync_periodic";

    public SmsSyncWorker(@NonNull Context context,
                         @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SmsSyncManager.syncNow(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            Log.e("SmsSyncWorker", "doWork error", e);
            return Result.retry();
        }
    }

    // 15분 주기 백업 동기화 스케줄
    public static void schedulePeriodic(Context context) {
        Constraints cons = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest req =
                new PeriodicWorkRequest.Builder(SmsSyncWorker.class,
                        15, TimeUnit.MINUTES)
                        .setConstraints(cons)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        req
                );
    }
}
