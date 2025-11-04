package apps.kr.smsmanager.ui;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.telephony.SmsManager;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;
import apps.kr.smsmanager.db.MessageDao;

public class NewMessageActivity extends BaseActivity {

    private EditText etTo, etMessage;
    private TextView btnSend;

    private final Executor dbExecutor = Executors.newSingleThreadExecutor();
    private static final int MAX_SEGMENTS = 3; // 최대 3분할까지만 허용

    // SEND_SMS 권한 런처
    private final ActivityResultLauncher<String> smsPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    sendSmsInternal();
                } else {
                    Toast.makeText(this, "문자 전송 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_message);   // 아래에 레이아웃 예시 있음

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        etTo = findViewById(R.id.etTo);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendSms());
    }

    private void sendSms() {
        String destNumber = etTo.getText().toString().trim();
        String msg = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(destNumber)) {
            Toast.makeText(this, "전화번호를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(msg)) {
            Toast.makeText(this, "메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smsPermLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        sendSmsInternal();
    }

    private void sendSmsInternal() {
        String destNumber = etTo.getText().toString().trim();
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(destNumber) || TextUtils.isEmpty(msg)) return;

        try {
            SmsManager sms = (Build.VERSION.SDK_INT >= 23)
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();

            ArrayList<String> parts = sms.divideMessage(msg);
            if (parts.size() > MAX_SEGMENTS) {
                Toast.makeText(
                        this,
                        "문자 길이가 너무 깁니다.\nsms만 발송 가능합니다.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            sms.sendMultipartTextMessage(destNumber, null, parts, null, null);

            insertSentSmsToSystemAndDb(destNumber, msg);

            Toast.makeText(this, "전송 완료", Toast.LENGTH_SHORT).show();
            hideKeyboardAndFinish();

        } catch (SecurityException se) {
            Toast.makeText(this, "권한 오류: SEND_SMS", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "전송 실패: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void insertSentSmsToSystemAndDb(String destNumber, String msg) {
        long now = System.currentTimeMillis();
        Uri inserted = null;

        try {
            ContentValues cv = new ContentValues();
            cv.put(Telephony.Sms.ADDRESS, destNumber);
            cv.put(Telephony.Sms.BODY, msg);
            cv.put(Telephony.Sms.DATE, now);
            cv.put(Telephony.Sms.READ, 1);
            cv.put(Telephony.Sms.SEEN, 1);
            cv.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);

            inserted = getContentResolver().insert(Telephony.Sms.Sent.CONTENT_URI, cv);
        } catch (Exception e) {
            android.util.Log.e("NewMessage", "insert sent sms to provider failed", e);
        }

        long sysId = 0L;
        if (inserted != null) {
            try {
                sysId = Long.parseLong(inserted.getLastPathSegment());
            } catch (Exception ignore) {}
        }
        if (sysId == 0L) {
            android.util.Log.w("NewMessage", "unknown sysId for sent sms, skip local DB");
            return;
        }

        final long finalSysId = sysId;
        final long finalNow = now;
        final String finalMsg = msg;
        final String finalAddr = destNumber;

        dbExecutor.execute(() -> {
            Context appCtx = getApplicationContext();
            AppDatabase db = AppDatabase.get(appCtx);
            MessageDao dao = db.messageDao();

            LocalMessage lm = new LocalMessage();
            lm.sysId = finalSysId;
            lm.isMms = false;
            lm.threadId = 0;
            lm.address = finalAddr;
            lm.body = finalMsg;
            lm.date = finalNow;
            lm.box = 2;        // 발신
            lm.uploaded = false;

            dao.upsert(lm);
        });
    }

    private void hideKeyboardAndFinish() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        finish();
    }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, NewMessageActivity.class);
        ctx.startActivity(i);
    }
}

