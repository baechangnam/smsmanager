package apps.kr.smsmanager.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class RespondViaMessageService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        // respond-via-message는 보통 바인드형으로 옴
        Log.d("ROLE_SMS", "RespondViaMessageService onBind: " + intent);
        // TODO: 여기서 intent.getData() 로 번호, intent.getStringExtra("android.intent.extra.TEXT") 로 문구 받아서
        // 너희 쪽 전송 로직으로 넘기면 됨
        return null; // 단순 예제. 실제로는 Binder 주는 구현을 하거나, 여기서 바로 보내도 됨
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ROLE_SMS", "RespondViaMessageService onStartCommand: " + intent);
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}