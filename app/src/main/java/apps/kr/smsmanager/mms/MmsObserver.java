package apps.kr.smsmanager.mms;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.Nullable;

import apps.kr.smsmanager.common.MmsUtils;

// MmsObserver.java
public class MmsObserver extends ContentObserver {
    private static final String TAG = "MmsObserver";
    private final Context appCtx;

    public MmsObserver(Context ctx, Handler handler) {
        super(handler);                 // ★ 이 핸들러의 루퍼(백그라운드)에서 onChange 호출
        this.appCtx = ctx.getApplicationContext();
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        try {
            // ① 우리 프로세스가 방금 쓴 변화면 스킵 (중복 방지)
            if (selfChange) return;

            long latestId = queryLatestMmsId(appCtx);
            if (latestId <= 0) return;

            // ② DB에 쓰지 말고 UI만 갱신
            String body = MmsUtils.readMmsText(appCtx, latestId);
            String from = MmsUtils.readMmsAddress(appCtx, latestId);
            if (body == null || body.isEmpty()) body = "[MMS]";

            Intent ui = new Intent("apps.kr.smsmanager.MMS_RECEIVED_INTERNAL");
            ui.setPackage(appCtx.getPackageName());
            ui.putExtra("mms_id", latestId);
            ui.putExtra("from", from);
            ui.putExtra("body", body);
            appCtx.sendBroadcast(ui);

        } catch (Throwable t) {
            Log.e(TAG, "onChange fail", t);
        }
    }

    private long queryLatestMmsId(Context ctx) {
        Uri inbox = Telephony.Mms.Inbox.CONTENT_URI;
        try (Cursor c = ctx.getContentResolver().query(
                inbox, new String[]{"_id"}, null, null, "date DESC LIMIT 1")) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        }
        return 0L;
    }
}

