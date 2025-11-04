package apps.kr.smsmanager.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.common.CommonUtils;
import apps.kr.smsmanager.common.CommonValue;



public class BaseActivity extends AppCompatActivity {
    Context mContext;
    String device_id = "";
    AppCompatDialog progressDialog;
    Activity baseActivity;
    double myLat;
    double myLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = BaseActivity.this;
        baseActivity = BaseActivity.this;
        device_id = CommonUtils.getDeviceID(mContext);

    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();

        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), decor);
        // 2) nav bar 컬러를 흰색으로
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(
                    ContextCompat.getColor(this, R.color.white)
            );
            wic.setAppearanceLightNavigationBars(true);
        }

        // 3) 데코 뷰 레퍼런스


        // 4) status bar 스크림 뷰 생성 & 붙이기
        View statusBarScrim = new View(this);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP
        );
        decor.addView(statusBarScrim, statusLp);

        // 5) 인셋 리스너 등록 → status bar 높이만큼 스크림 높이, 색 업데이트
        ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
            // status bar 높이(보이는 여부와 관계없이)
            int sbHeight = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.statusBars()
            ).top;

            // 스크림 높이 설정
            statusLp.height = sbHeight;
            statusBarScrim.setLayoutParams(statusLp);
            // 원하는 빨간색으로 칠하기
            statusBarScrim.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.black)
            );

            // 콘텐츠는 status+nav 만큼 패딩
            Insets navInsets = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
            );
            View content = v.findViewById(android.R.id.content);
            if (content != null) {
                content.setPadding(
                        0,               // left
                        sbHeight,        // top
                        0,               // right
                        navInsets.bottom // bottom
                );
            }

            return insets;
        });
        // 6) 강제 인셋 요청
        ViewCompat.requestApplyInsets(decor);
    }

    public void progressSET() {
        if (progressDialog == null || !progressDialog.isShowing()) {
            return;
        }

    }


    public void progressOFF() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    public void exitApp() {
        finish();
        System.exit(0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createNotification() {
        NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        String id = "smsmoa";
        CharSequence name = "smsmoa";
        String description = "smsmoa desc";
        NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.enableVibration(true);
        mChannel.setLightColor(Color.RED);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        mChannel.setSound(RingtoneManager
                .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);


        mChannel.setShowBadge(true);
        mNotificationManager.createNotificationChannel(mChannel);
    }



    public void showErrorDialogNoCancel(String msg) {
        AlertDialog.Builder adialog = new AlertDialog.Builder(
                BaseActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        adialog.setMessage(msg)
                .setPositiveButton("확인",
                        (dialog, which) -> finish())
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });

        AlertDialog alert = adialog.create();
        alert.show();
    }

    public void showErrorDialog(String msg) {
        AlertDialog.Builder adialog = new AlertDialog.Builder(
                BaseActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        adialog.setMessage(msg)
                .setPositiveButton("확인",
                        (dialog, which) -> dialog.dismiss());

        AlertDialog alert = adialog.create();
        alert.show();
    }

    public void showErrorDialogTheme(String msg) {
        AlertDialog.Builder adialog = new AlertDialog.Builder(
                BaseActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        adialog.setMessage(msg)
                .setPositiveButton("확인",
                        (dialog, which) -> dialog.dismiss());

        AlertDialog alert = adialog.create();
        alert.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


}
