package apps.kr.smsmanager.common;

// apps/kr/smsmanager/notify/NotificationHelper.java


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.ui.MainActivity;

public class NotificationHelper {

    public static final String CH_ID_SMS = "sms_incoming";
    public static final int NOTI_ID_SMS_BASE = 2000;

    public static void showIncomingMms(Context ctx, String from, String body, long sysId, long threadId) {
        // 탭 시 메인으로
        Intent i = new Intent(ctx, apps.kr.smsmanager.ui.MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("open_thread_id", threadId);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, (int)(sysId & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // (선택) 그룹 키로 SMS/MMS 묶기
        String groupKey = "apps.kr.smsmanager.NOTI.MSG";

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CH_ID_SMS)
                .setSmallIcon(R.drawable.icon)   // 프로젝트 아이콘으로 교체
                .setContentTitle(from == null ? "새 MMS" : from)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(groupKey);

        NotificationManagerCompat.from(ctx).notify((int)(sysId & 0x7fffffff), nb.build());
    }

    public static void showIncomingSms(Context ctx, String from, String body) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        // 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID_SMS,
                    "SMS 수신",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("새로 수신된 문자 메시지 알림");
            ch.enableLights(true);
            ch.setLightColor(Color.GREEN);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        // 알림 눌렀을 때 메인으로
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                ctx,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 본문은 너무 길면 잘라주자
        String content = body;
        if (content != null && content.length() > 40) {
            content = content.substring(0, 40) + "…";
        }

        Notification n = new NotificationCompat.Builder(ctx, CH_ID_SMS)
                .setSmallIcon(R.drawable.icon) // 없으면 앱 아이콘으로 바꿔도 됨
                .setContentTitle(from == null ? "새 문자" : from)
                .setContentText(content == null ? "(내용 없음)" : content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        // 여러 개 와도 겹치지 않게 타임스탬프 기반으로 ID 발급
        int notiId = (int) (System.currentTimeMillis() % 100000);
        nm.notify(notiId, n);
    }
}
