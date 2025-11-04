// =============================================
// SplashActivity.java
// 순서: 네트워크 체크 → 배터리 최적화 제외 → 알림 권한(33+) → ROLE_SMS → FGS 시작 → 메인 이동
// 하나라도 거부되면 앱 종료
// =============================================
package apps.kr.smsmanager.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import apps.kr.smsmanager.R;

public class SplashActivity extends AppCompatActivity {

    private static final int REQ_PERM_NOTI = 0x11; // for POST_NOTIFICATIONS (API 33+)
    private static final int REQ_PERM_PHONE = 0x12;  // ✅ 추가
    // ROLE_SMS 요청 런처
    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // 방법 A: 결과코드 확인
                boolean ok = (result.getResultCode() == Activity.RESULT_OK) && isRoleSmsHeld();
                // 방법 B: 안전하게 role 재확인
                if (isRoleSmsHeld()) {
                    requestBatteryOptimizationExemption();
                } else {
                    // 다시 launch 시도 금지(무한루프/무반응 유발) → 명확히 종료 or 설정 화면 유도
                    new AlertDialog.Builder(this)
                            .setTitle("기본 문자앱 전환 실패")
                            .setMessage("설정 > 기본 앱에서 문자앱을 이 앱으로 바꿔주세요.")
                            .setPositiveButton("설정 열기", (d,w) -> {
                                try {
                                    // 기본앱 화면(제조사별 다를 수 있음)
                                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                                } catch (Exception e) {
                                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:" + getPackageName())));
                                }
                                // 유저가 바꿔오면 onResume에서 isRoleHeld() 재체크 권장
                            })
                            .setNegativeButton("종료", (d,w) -> hardExit(null))
                            .setCancelable(false)
                            .show();
                }
            });

    // 배터리 최적화 제외 런처
    private final ActivityResultLauncher<Intent> batteryOptLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // 사용자가 다이얼로그에서 허용했는지 실제 상태로 재확인
                if (isIgnoringBatteryOptimizations()) {
                    requestNotificationPermission();
                } else {
                    // 기기에 따라 다이얼로그가 무시되거나 사용자가 취소했을 수 있음 → 설정 화면으로 유도
                    new AlertDialog.Builder(this)
                            .setTitle("배터리 최적화 제외 필요")
                            .setMessage("설정에서 이 앱을 배터리 최적화 제외로 켜 주세요.")
                            .setCancelable(false)
                            .setPositiveButton("설정으로 이동", (d, w) -> {
                                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            })
                            .setNegativeButton("종료", (d, w) -> hardExit(null))
                            .show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);


        // 1) 네트워크 체크
        if (!isNetworkAvailable()) {
            hardExit(getString(R.string.toast_network_error));
            return;
        }
        // 2) 배터리 최적화 제외
        requestBatteryOptimizationExemption();
    }

    // ===== STEP 1: 네트워크 체크 =====
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    // ===== STEP 2: 배터리 최적화 제외 =====
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // 바로 알림 권한으로
            requestNotificationPermission();
            return;
        }
        if (isIgnoringBatteryOptimizations()) {
            requestNotificationPermission();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("배터리 최적화 제외")
                .setMessage("백그라운드 동기화를 위해 배터리 최적화에서 제외해야 합니다.")
                .setCancelable(false)
                .setPositiveButton("허용", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        batteryOptLauncher.launch(intent);
                    } catch (ActivityNotFoundException e) {
                        // 일부 기기는 직접 설정으로
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("종료", (d, w) -> hardExit(null))
                .show();
    }


    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    // ===== STEP 3: 알림 권한 (API 33+) =====
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String perm = android.Manifest.permission.POST_NOTIFICATIONS;
            if (ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("알림 권한 필요")
                        .setMessage("문자 동기화 상태 알림 유지를 위해 알림 권한이 필요합니다.")
                        .setCancelable(false)
                        .setPositiveButton("허용", (d, w) -> ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_PERM_NOTI))
                        .setNegativeButton("종료", (d, w) -> hardExit(null))
                        .show();
                return;
            }
        }
        // 다음 단계
        requestPhoneNumberPermission();
    }

    private void requestPhoneNumberPermission() {
        // 마시멜로 이전이면 런타임 권한 없음
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            requestRoleSms();
            return;
        }

        String perm;
        // Android 8.0(API 26)+ 에서는 READ_PHONE_NUMBERS 우선
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perm = android.Manifest.permission.READ_PHONE_NUMBERS;
        } else {
            perm = android.Manifest.permission.READ_PHONE_STATE;
        }

        if (ContextCompat.checkSelfPermission(this, perm)
                == PackageManager.PERMISSION_GRANTED) {
            // 이미 허용 → 다음 단계
            requestRoleSms();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("전화번호 권한 필요")
                .setMessage("서버에 단말 전화번호를 함께 전송하기 위해 전화 상태/번호 권한이 필요합니다.")
                .setCancelable(false)
                .setPositiveButton("허용", (d, w) ->
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{perm},
                                REQ_PERM_PHONE
                        )
                )
                .setNegativeButton("종료", (d, w) -> hardExit(null))
                .show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERM_NOTI) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                String perm = android.Manifest.permission.POST_NOTIFICATIONS;
                if (ContextCompat.checkSelfPermission(this, perm)
                        != PackageManager.PERMISSION_GRANTED) {
                    hardExit("알림 권한을 허용해야 합니다.");
                    return;
                }
            }
            // ✅ 알림 OK → 전화번호 권한으로
            requestPhoneNumberPermission();
            return;
        }

        if (requestCode == REQ_PERM_PHONE) {
            String perm;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                perm = android.Manifest.permission.READ_PHONE_NUMBERS;
            } else {
                perm = android.Manifest.permission.READ_PHONE_STATE;
            }

            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                // 하나라도 거부되면 종료
                hardExit("전화번호 권한을 허용해야 합니다.");
                return;
            }
            // ✅ 전화번호 권한 OK → ROLE_SMS 단계
            requestRoleSms();
        }
    }

    // ===== STEP 4: ROLE_SMS =====
    private void requestRoleSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm == null) {
                log("RoleManager null");
                hardExit("이 기기에서 기본 문자앱 변경을 지원하지 않습니다.");
                return;
            }
            if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                log("ROLE_SMS not available");
                hardExit("기본 문자앱 역할을 사용할 수 없는 기기입니다.");
                return;
            }
            if (!rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                // 후보 요건 미충족 시 여기서도 무반응 가능 → 사전 진단 로그 추천
                if (!isSmsRoleCandidateLikely()) {
                    log("Not a likely candidate: check receivers/intent-filters/permissions");
                }
                try {
                    roleRequestLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS));
                    return;
                } catch (ActivityNotFoundException e) {
                    log("createRequestRoleIntent ANFE: " + e);
                }
            }
        }
        // 구버전 or 이미 기본앱 → 다음 단계
        startSyncServiceAndGoMain();
    }

    private boolean isSmsRoleCandidateLikely() {
        // 아주 간단한 사전 체크(권장): 실제로는 PackageManager로 등록된 리시버 탐색해봐도 됨
        // 여기선 true만 반환하고 로그로 가이드
        return true;
    }

    private void log(String s) { android.util.Log.d("ROLE_SMS", s); }


    private boolean isRoleSmsHeld() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_SMS);
        }
        return true; // 구버전은 true 취급
    }

    // ===== STEP 5: 서비스 시작 → 메인 =====
    private void startSyncServiceAndGoMain() {

        // 서비스가 즉시 startForeground() 호출해야 함 (5초 이내)

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        },2000);

    }

    // ===== 공통: 강제 종료 =====
    private void hardExit(String message) {
        if (message != null && !message.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("확인", (d, w) -> finishAffinity())
                    .show();
        } else {
            finishAffinity();
        }
    }
}

