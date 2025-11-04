package apps.kr.smsmanager.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.appcompat.app.AppCompatActivity;

public class QuickReplyActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();               // action=RESPOND_VIA_MESSAGE
        Uri data = i.getData();               // smsto:, sms:, mmsto:, mms:
        String body = i.getStringExtra("android.intent.extra.TEXT"); // 제안 문구

        // TODO: 여기서 바로 전송하거나 Compose로 넘겨도 됨.
        // 최소 스텁: 그냥 종료만
        finish();
    }
}