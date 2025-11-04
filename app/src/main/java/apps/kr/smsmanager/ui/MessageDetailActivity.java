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
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.telephony.SmsManager;
import apps.kr.smsmanager.R;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;
import apps.kr.smsmanager.db.MessageDao;

public class MessageDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ADDR   = "addr";
    public static final String EXTRA_BODY   = "body";
    public static final String EXTRA_DATE   = "date";   // millis
    public static final String EXTRA_IS_MMS = "isMms";  // optional

    private TextView tvAddr, tvDate, tvBody, btnSend;
    private EditText etMessage;
    private View focusGuard, bottomBar;

    private String destNumber;

    private final Executor dbExecutor = Executors.newSingleThreadExecutor();

    private static final int MAX_SEGMENTS = 3;

    // 권한 요청 런처 (SEND_SMS)
    private final ActivityResultLauncher<String> smsPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    // 권한 허용되면 방금 입력한 내용으로 다시 전송 시도
                    sendSmsInternal();
                } else {
                    Toast.makeText(this, "문자 전송 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_detail);

        // 키보드: 레이아웃은 리사이즈, 최초엔 숨김
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        );

        View root = findViewById(R.id.root);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            // 시스템바 + 키보드(IME) 모두 고려
            int bottom = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            ).bottom;

            v.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottom);
            return insets;
        });

        bindViews();
        bindDataFromIntent();
        bindActions();
    }
    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void bindViews() {
        focusGuard = findViewById(R.id.focusGuard);
        bottomBar  = findViewById(R.id.bottomBar);
        tvAddr     = findViewById(R.id.tvAddr);
        tvDate     = findViewById(R.id.tvDate);
        tvBody     = findViewById(R.id.tvBody);
        etMessage  = findViewById(R.id.etMessage);
        etMessage.setImeOptions(etMessage.getImeOptions() | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        etMessage.setOnEditorActionListener((v, actionId, e) -> false); // 엔터=줄바꿈
        btnSend    = findViewById(R.id.btnSend);
    }

    private void bindDataFromIntent() {
        Intent it = getIntent();
        destNumber = it.getStringExtra(EXTRA_ADDR);
        String body = it.getStringExtra(EXTRA_BODY);
        long dateMs = it.getLongExtra(EXTRA_DATE, 0L);

        tvAddr.setText(destNumber == null ? "(알 수 없음)" : destNumber);
        tvBody.setText(body == null ? "" : body);

        String dateText = (dateMs > 0)
                ? DateFormat.format("yyyy-MM-dd HH:mm", dateMs).toString()
                : "";
        tvDate.setText(dateText);
    }

    private void bindActions() {
        // IME 액션(키보드 전송 버튼)


        // 전송 버튼
        btnSend.setOnClickListener(v -> sendSms());
    }

    private void sendSms() {
        if (destNumber == null || destNumber.trim().isEmpty()) {
            Toast.makeText(this, "수신자 번호가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(this, "메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 권한 체크 → 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smsPermLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        // 권한 OK → 전송
        sendSmsInternal();
    }

    private void sendSmsInternal() {
        try {
            String msg = etMessage.getText().toString().trim();
            if (msg.isEmpty()) return;

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

            // ✅ 전송 요청 성공했다고 보고 → 시스템/DB 저장
            insertSentSmsToSystemAndDb(msg);

            Toast.makeText(this, "전송 완료", Toast.LENGTH_SHORT).show();
            afterSentSuccess();

        } catch (SecurityException se) {
            Toast.makeText(this, "권한 오류: SEND_SMS", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "전송 실패: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void insertSentSmsToSystemAndDb(String msg) {
        long now = System.currentTimeMillis();
        Uri inserted = null;

        // 1) 시스템 SMS Provider(Sent box)에 넣기
        try {
            ContentValues cv = new ContentValues();
            cv.put(Telephony.Sms.ADDRESS, destNumber);
            cv.put(Telephony.Sms.BODY, msg);
            cv.put(Telephony.Sms.DATE, now);
            cv.put(Telephony.Sms.READ, 1);
            cv.put(Telephony.Sms.SEEN, 1);
            cv.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT); // 2

            inserted = getContentResolver()
                    .insert(Telephony.Sms.Sent.CONTENT_URI, cv);
        } catch (Exception e) {
            android.util.Log.e("MessageDetail", "insert sent sms to provider failed", e);
        }

        long sysId = 0L;
        if (inserted != null) {
            try {
                sysId = Long.parseLong(inserted.getLastPathSegment());
            } catch (Exception ignore) {}
        }

        // 시스템 id를 못 얻었으면 DB까지는 안 넣음(중복/정합성 문제 방지용)
        if (sysId == 0L) {
            android.util.Log.w("MessageDetail", "unknown sysId for sent sms, skip local DB");
            return;
        }

        final long finalSysId = sysId;
        final long finalNow   = now;
        final String finalMsg = msg;
        final String finalAddr = destNumber;

        // 2) 로컬 DB에 저장 (Room)
        dbExecutor.execute(() -> {
            Context appCtx = getApplicationContext();
            AppDatabase db = AppDatabase.get(appCtx);
            MessageDao dao = db.messageDao();

            LocalMessage lm = new LocalMessage();
            lm.sysId = finalSysId;
            lm.isMms = false;
            lm.threadId = 0;          // 필요하면 나중에 threadId 조회해서 채워도 됨
            lm.address = finalAddr;
            lm.body = finalMsg;
            lm.date = finalNow;
            lm.box = 2;               // 1=inbox, 2=sent 이런 식으로 사용
            lm.uploaded = false;

            dao.upsert(lm);
        });
    }

    private void afterSentSuccess() {
        // 입력창 초기화 + 포커스 제거
        etMessage.setText("");
        etMessage.clearFocus();

        // 포커스 가드로 포커스 이동하여 키보드 재등장 방지
        if (focusGuard != null) focusGuard.requestFocus();

        // 키보드 내리기
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
            bottomBar.postDelayed(() -> {
                if (focusGuard != null) focusGuard.requestFocus();
                imm.hideSoftInputFromWindow(bottomBar.getWindowToken(), 0);
            }, 120);
        }
    }

    /* 편의: 외부에서 호출할 때 */
    public static void start(Context ctx, String addr, String body, long dateMs, boolean isMms) {
        Intent i = new Intent(ctx, MessageDetailActivity.class);
        i.putExtra(EXTRA_ADDR, addr);
        i.putExtra(EXTRA_BODY, body);
        i.putExtra(EXTRA_DATE, dateMs);
        i.putExtra(EXTRA_IS_MMS, isMms);
        ctx.startActivity(i);
    }
}
