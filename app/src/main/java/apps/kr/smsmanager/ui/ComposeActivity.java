package apps.kr.smsmanager.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;


import androidx.appcompat.app.AppCompatActivity;


import apps.kr.smsmanager.R;


public class ComposeActivity extends AppCompatActivity {
    private EditText toView; private EditText bodyView;


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complse);
        toView = findViewById(R.id.et_to);
        bodyView = findViewById(R.id.et_body);
        Button btnSend = findViewById(R.id.btn_send);


// 인텐트로부터 주소/body 프리필
        Intent i = getIntent();
        Uri data = i.getData();
        if (data != null && ("sms".equals(data.getScheme()) || "smsto".equals(data.getScheme()))) {
            String addr = data.getSchemeSpecificPart();
            if (!TextUtils.isEmpty(addr)) toView.setText(addr);
        }
        String body = i.getStringExtra("sms_body");
        if (!TextUtils.isEmpty(body)) bodyView.setText(body);


        btnSend.setOnClickListener(v -> doSend());
    }


    private void doSend() {
        String to = toView.getText().toString();
        String text = bodyView.getText().toString();
// TODO: Subscription 선택 등 고려하여 SmsManager로 전송하고 보낸함 기록
// SmsManager sms = SmsManager.getDefault(); sms.sendTextMessage(to, null, text, sentPI, deliveryPI);
        finish();
    }
}
