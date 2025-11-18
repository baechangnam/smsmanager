package apps.kr.smsmanager.ui;

import static apps.kr.smsmanager.common.MmsUtils.isBackgroundDataRestricted;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.db.AppDatabase;
import apps.kr.smsmanager.db.LocalMessage;
import apps.kr.smsmanager.db.MessageDao;
import apps.kr.smsmanager.model.MsgItem;
import apps.kr.smsmanager.sync.SyncService;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends BaseActivity {

    // ================== pref key ==================
    private static final String PREF = "sms_prefs";
    private static final String KEY_SERVER_NAME = "server_name";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_SERVER_ON = "server_on";
    private static final String KEY_INIT_DONE = "init_done";

    // ================== default ==================
    private static final String DEFAULT_SERVER_NAME = "TEST-SERVER-01";
    private static final String DEFAULT_SERVER_URL = "https://192.168.0.10/sms/upload";

    // ================== UI ==================
    private SmsAdapter adapter;
    private TextView tvServerName, tvServerUrl;
    private Switch swServerOnOff;
    private View progress;

    // ================== async ==================
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    RecyclerView rv;
    private boolean backgroundDialogShown = false;

    @Override
    protected void onResume() {
        super.onResume();
        checkBackgroundRestrictions();
    }

    private void checkBackgroundRestrictions() {
        if (backgroundDialogShown) return;

        if (isBackgroundDataRestricted(this)) {
            backgroundDialogShown = true;

            new AlertDialog.Builder(this)
                    .setTitle("백그라운드 데이터 제한")
                    .setMessage(
                            "데이터 절약 모드 또는 백그라운드 데이터 제한으로 인해\n" +
                                    "앱이 백그라운드에서 서버로 문자를 전송하지 못할 수 있습니다.\n\n" +
                                    "설정 화면으로 이동해서 이 앱의 백그라운드 데이터 사용을 허용해 주세요."
                    )
                    .setPositiveButton("설정 열기", (d, w) -> {
                        openBackgroundDataSettings();
                    })
                    .setNegativeButton("나중에", null)
                    .show();
        }
    }

    private void openBackgroundDataSettings() {
        Intent intent = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Data Saver에서 이 앱 예외로 추가하는 화면
            intent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        }

        if (intent == null || intent.resolveActivity(getPackageManager()) == null) {
            // 폴백: 앱 상세 설정 화면 (여기서 데이터/배터리 설정 들어갈 수 있음)
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        }

        startActivity(intent);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollMessages();
            // 다음 폴링 예약
            mainHandler.postDelayed(this, 2000); // 2초
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progress = findViewById(R.id.progress);
        tvServerName = findViewById(R.id.tvServerName);
        tvServerUrl = findViewById(R.id.tvServerUrl);
        swServerOnOff = findViewById(R.id.swServerOnOff);
        ImageButton btnSettings = findViewById(R.id.btnServerSettings);

        rv = findViewById(R.id.rvSms);
        adapter = new SmsAdapter();
        rv.setAdapter(adapter);
        attachSwipeReveal();

        ImageButton btnNewMessage = findViewById(R.id.btnNewMessage);
        btnNewMessage.setOnClickListener(v -> {
            NewMessageActivity.start(MainActivity.this);
        });

        adapter.setOnItemClickListener(it -> {
            // 상세 화면 이동
            Intent i = new Intent(MainActivity.this, MessageDetailActivity.class);
            i.putExtra(MessageDetailActivity.EXTRA_ADDR, it.address);
            i.putExtra(MessageDetailActivity.EXTRA_BODY, it.body);
            i.putExtra(MessageDetailActivity.EXTRA_DATE, it.date);
            i.putExtra(MessageDetailActivity.EXTRA_IS_MMS, it.isMms);
            startActivity(i);
        });

        adapter.setOnDeleteClickListener(item -> {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("삭제")
                    .setMessage("이 메시지를 삭제할까요?")
                    .setPositiveButton("삭제", (d, w) -> {
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.get(getApplicationContext());
                            db.messageDao().deleteByKey(item.id, item.isMms);
                            // LiveData observeLatest 가 알아서 UI 다시 그림
                        });
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });


        // prefs 로드
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String name = sp.getString(KEY_SERVER_NAME, DEFAULT_SERVER_NAME);
        String url = sp.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
        boolean on = sp.getBoolean(KEY_SERVER_ON, true);

        // ✅ prefs 에 값이 없으면 기본 URL 저장
        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_SERVER_URL;
            sp.edit().putString(KEY_SERVER_URL, url).apply();
        }

        tvServerName.setText(name);
        tvServerUrl.setText(url);
        swServerOnOff.setChecked(on);

        if (on) {
            Intent svc = new Intent(this, SyncService.class);
            ContextCompat.startForegroundService(this, svc);
        }

        swServerOnOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(KEY_SERVER_ON, isChecked).apply();

            Intent svc = new Intent(MainActivity.this, SyncService.class);

            if (isChecked) {
                // ON → 포그라운드 서비스 시작
                ContextCompat.startForegroundService(MainActivity.this, svc);
            } else {
                // OFF → 서비스 중지
                stopService(svc);
            }
        });


        btnSettings.setOnClickListener(v -> showServerSettingsDialog());

        // 👇 여기서 최초 import or DB 로드
        ensureImportedOnce();
        observeDb();
    }

    private void attachSwipeReveal() {
        int swipeDir = androidx.recyclerview.widget.ItemTouchHelper.LEFT;

        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback cb =
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, swipeDir) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        // 여기서는 "완전 스와이프 → 삭제" 안 쓸 거라 그냥 원복
                        int pos = viewHolder.getBindingAdapterPosition();
                        if (pos >= 0) {
                            adapter.notifyItemChanged(pos);
                        }
                    }

                    @Override
                    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                        // onSwiped 잘 안 들어오게 크게
                        return 1.0f;
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY,
                                            int actionState, boolean isCurrentlyActive) {

                        if (actionState != androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            super.onChildDraw(c, recyclerView, viewHolder, dX, dY,
                                    actionState, isCurrentlyActive);
                            return;
                        }

                        View fg = viewHolder.itemView.findViewById(R.id.fgContent);
                        View btn = viewHolder.itemView.findViewById(R.id.btnDelete);

                        if (fg == null || btn == null) {
                            super.onChildDraw(c, recyclerView, viewHolder, dX, dY,
                                    actionState, isCurrentlyActive);
                            return;
                        }

                        int maxReveal = btn.getWidth() + 32; // 버튼 폭 + 여유

                        if (isCurrentlyActive) {
                            // 손가락 움직이는 동안만 dX 반영
                            float clampedDX = Math.max(-maxReveal, Math.min(0, dX));
                            fg.setTranslationX(clampedDX);
                        } else {
                            // 손 뗀 이후에는 dX 무시 → 마지막 위치 그대로 유지
                            // (여기서 굳이 다시 setTranslationX 안 해도 됨, 이미 값이 있으니까)
                        }
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        // 너무 살짝만 밀린 경우는 자동으로 다시 닫아주자
                        View fg = viewHolder.itemView.findViewById(R.id.fgContent);
                        View btn = viewHolder.itemView.findViewById(R.id.btnDelete);
                        if (fg != null && btn != null) {
                            int maxReveal = btn.getWidth() + 32;
                            if (Math.abs(fg.getTranslationX()) < maxReveal * 0.3f) {
                                // 거의 안 밀렸으면 닫기
                                fg.animate().translationX(0).setDuration(120).start();
                            } else if (Math.abs(fg.getTranslationX()) < maxReveal) {
                                // 애매하게 열렸으면 딱 버튼까지 열어주기
                                fg.animate().translationX(-maxReveal).setDuration(120).start();
                            }
                        }
                    }
                };

        new androidx.recyclerview.widget.ItemTouchHelper(cb).attachToRecyclerView(rv);
    }

    private void observeDb() {
        MessageDao dao = AppDatabase.get(getApplicationContext()).messageDao();
        // 최신 200개를 구독
        dao.observeLatest(200).observe(this, list -> {
            if (list == null) return;
            List<MsgItem> ui = mapToUi(list);
            adapter.setItems(ui);
            if (!ui.isEmpty()) rv.scrollToPosition(0);
            showLoading(false);
        });
    }

    // 최초 1회만 시스템에서 긁어서 DB에 넣기
    private void ensureImportedOnce() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boolean done = sp.getBoolean(KEY_INIT_DONE, false);
        if (done) {
            // observeDb가 있으므로 추가 작업 불필요
            showLoading(false);
            return;
        }

        showLoading(true);

        executor.execute(() -> {
            Context ctx = getApplicationContext();
            MessageDao dao = AppDatabase.get(ctx).messageDao();

            // 1) 시스템에서 SMS/MMS 가져오기 — 최초 1회만
            List<MsgItem> sms = loadAllSmsFromSystem(ctx);
            List<MsgItem> mms = loadAllMmsFromSystem(ctx);

            List<LocalMessage> toInsert = new ArrayList<>(sms.size() + mms.size());

            for (MsgItem it : sms) {
                LocalMessage lm = new LocalMessage();
                lm.sysId = it.id;
                lm.isMms = false;
                lm.threadId = it.threadId;
                lm.address = it.address;
                lm.body = it.body;
                lm.date = it.date;
                lm.box = 1;
                lm.uploaded = false;
                toInsert.add(lm);
            }

            for (MsgItem it : mms) {
                LocalMessage lm = new LocalMessage();
                lm.sysId = it.id;
                lm.isMms = true;
                lm.threadId = it.threadId;
                lm.address = it.address;
                lm.body = (it.body == null || it.body.isEmpty()) ? "[MMS]" : it.body;
                lm.date = it.date;
                lm.box = 1;
                lm.uploaded = false;
                toInsert.add(lm);
            }

            // 2) DB에 한꺼번에 insert
            dao.upsertAll(toInsert);

            // 3) flag
            sp.edit().putBoolean(KEY_INIT_DONE, true).apply();

            // observeDb가 이미 걸려 있어서 여기서 UI setItems는 필요 없음
        });
    }

    // DB → UI

    private List<MsgItem> mapToUi(List<LocalMessage> src) {
        List<MsgItem> out = new ArrayList<>(src.size());
        for (LocalMessage m : src) {
            out.add(new MsgItem(
                    m.sysId,
                    m.threadId,
                    m.address,
                    m.body,
                    m.date,
                    m.isMms,
                    m.box          // ✅ 추가
            ));
        }
        return out;
    }

    private void showLoading(boolean show) {
        if (progress != null) {
            progress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ================== 브로드캐스트로 들어온 신규 SMS/MMS ==================
    @Override
    protected void onStart() {
        super.onStart();

        // SMS
        IntentFilter smsF = new IntentFilter("apps.kr.smsmanager.SMS_RECEIVED_INTERNAL");
        registerReceiver(smsUiReceiver, smsF, Context.RECEIVER_NOT_EXPORTED);

        // MMS
        IntentFilter mmsF = new IntentFilter("apps.kr.smsmanager.MMS_RECEIVED_INTERNAL");
        registerReceiver(mmsUiReceiver, mmsF, Context.RECEIVER_NOT_EXPORTED);

      //  mainHandler.post(pollRunnable);   // 폴링 시작
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(smsUiReceiver);
        unregisterReceiver(mmsUiReceiver);

     //   mainHandler.removeCallbacks(pollRunnable); // 폴링 중지
    }

    private void pollMessages() {
        executor.execute(() -> {
            Context ctx = getApplicationContext();

            // 1) 시스템에서 SMS/MMS 최신꺼들 가져오기
            List<MsgItem> sms = loadAllSmsFromSystem(ctx);
            List<MsgItem> mms = loadAllMmsFromSystem(ctx);

            // 2) 섞어서 최신순
            List<MsgItem> all = new ArrayList<>(sms.size() + mms.size());
            all.addAll(sms);
            all.addAll(mms);
            all.sort((a, b) -> Long.compare(b.date, a.date));

            // 3) 어댑터에 이미 있는 맨위 아이템과 비교해서 같으면 패스
            MsgItem currentTop = adapter.getItem(0);   // ↓ 이거 너 방금 만든거
            MsgItem newTop = all.isEmpty() ? null : all.get(0);

            if (newTop == null) return;
            if (currentTop != null &&
                    currentTop.id == newTop.id &&
                    currentTop.isMms == newTop.isMms) {
                // 변화 없음
                return;
            }

            // 4) 변화 있으면 UI 갱신
            mainHandler.post(() -> {
                adapter.setItems(all);
                rv.scrollToPosition(0);
            });
        });
    }

    // 새로 들어온 1건만 위에 붙이는 방식
    private final BroadcastReceiver smsUiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // DB에 이미 insert 되어있다고 가정하고, DB에서 가장 최근 것만 꺼낸다
            Log.d("UI-SMS", ">>> got internal sms! from=" +
                    intent.getStringExtra("from"));

         //   refreshFromDb();
        }
    };

    private final BroadcastReceiver mmsUiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("MmsWapPushReceiver", ">>> got internal mms! from=" + intent.getStringExtra("from"));
        //    refreshFromDb();

         //   mainHandler.postDelayed(() -> refreshFromDb(), 1000);
        }
    };

    private void refreshFromDb() {
        executor.execute(() -> {
            MessageDao dao = AppDatabase.get(getApplicationContext()).messageDao();
            List<LocalMessage> latest = dao.getLatest(50);
            if (latest == null || latest.isEmpty()) return;
            List<MsgItem> ui = mapToUi(latest);
            mainHandler.post(() -> {
                adapter.setItems(ui);
                rv.scrollToPosition(0);
            });
        });
    }

    // ================== 서버 설정 다이얼로그 ==================
    private void showServerSettingsDialog() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String curName = sp.getString(KEY_SERVER_NAME, DEFAULT_SERVER_NAME);
        String curUrl = sp.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
        boolean on = sp.getBoolean(KEY_SERVER_ON, true);


        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_settings, null, false);
        EditText etName = dialogView.findViewById(R.id.etServerName);
        EditText etUrl = dialogView.findViewById(R.id.etServerUrl);
        etName.setText(curName);
        etUrl.setText(curUrl);

        new AlertDialog.Builder(this)
                .setTitle("서버 설정")
                .setView(dialogView)
                .setPositiveButton("저장", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newUrl = etUrl.getText().toString().trim();

                    if (newName.isEmpty()) newName = DEFAULT_SERVER_NAME;
                    if (newUrl.isEmpty()) newUrl = DEFAULT_SERVER_URL;

                    tvServerName.setText(newName);
                    tvServerUrl.setText(newUrl);

                    getSharedPreferences(PREF, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_SERVER_NAME, newName)
                            .putString(KEY_SERVER_URL, newUrl)
                            .apply();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ================== 시스템에서 처음에만 싹 긁는 함수들 ==================

    // 전체 SMS
    private List<MsgItem> loadAllSmsFromSystem(Context ctx) {
        List<MsgItem> list = new ArrayList<>();
        Uri smsUri = Telephony.Sms.Inbox.CONTENT_URI;

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    smsUri,
                    new String[]{
                            Telephony.Sms._ID,
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.THREAD_ID
                    },
                    null,
                    null,
                    Telephony.Sms.DATE + " DESC"
            );
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String addr = c.getString(1);
                    String body = c.getString(2);
                    long date = c.getLong(3);
                    long threadId = c.getLong(4);

                    list.add(new MsgItem(id, threadId, addr, body, date, false,1));
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "loadAllSmsFromSystem fail", e);
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    // 전체 MMS
    private List<MsgItem> loadAllMmsFromSystem(Context ctx) {
        List<MsgItem> list = new ArrayList<>();

        Uri mmsUri = Uri.parse("content://mms/inbox");
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    mmsUri,
                    new String[]{"_id", "date", "thread_id"},
                    null,
                    null,
                    "date DESC"
            );
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    long sec = c.getLong(1);
                    long threadId = c.getLong(2);
                    long date = sec * 1000L; // mms는 초단위라서 ms로 바꿔줌

                    String addr = apps.kr.smsmanager.common.MmsUtils.getMmsAddress(ctx, id);
                    String body = apps.kr.smsmanager.common.MmsUtils.getMmsText(ctx, id);
                    if (body == null || body.isEmpty()) body = "[MMS]";

                    list.add(new MsgItem(id, threadId, addr, body, date, true,1));
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "loadAllMmsFromSystem fail", e);
        } finally {
            if (c != null) c.close();
        }

        return list;
    }



}
