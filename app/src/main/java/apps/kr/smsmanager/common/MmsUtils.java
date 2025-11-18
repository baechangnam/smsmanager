package apps.kr.smsmanager.common;


import static android.content.Context.TELEPHONY_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;


public class MmsUtils {
    public static String readMmsText(Context ctx, long mmsId) {
        StringBuilder sb = new StringBuilder();
        Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
        try (Cursor pc = ctx.getContentResolver().query(partUri,
                new String[]{"_id", "ct", "text"}, null, null, null)) {
            if (pc == null) return "";
            while (pc.moveToNext()) {
                String ct = pc.getString(1); // content-type
                if (ct != null && (ct.startsWith("text/"))) {
                    String text = pc.getString(2);
                    if (!TextUtils.isEmpty(text)) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static boolean isBackgroundDataRestricted(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int status = cm.getRestrictBackgroundStatus();
            // ENABLED  : Data Saver 켜져 있고 이 앱은 예외 아님 → 백그 제한
            // WHITELISTED: Data Saver 켰지만 이 앱은 예외 → OK
            // DISABLED : Data Saver 안 켜짐 → OK
            return status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        }

        // N 미만은 Data Saver 없으니 여기선 특별히 막히는 거 없음
        return false;
    }

    // (옵션) 현재 네트워크 연결 유무도 같이 쓰고 싶으면
    public static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
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
    public static String getDevicePhoneNumber(Context ctx) {
        try {
            String perm;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                perm = android.Manifest.permission.READ_PHONE_NUMBERS;
            } else {
                perm = android.Manifest.permission.READ_PHONE_STATE;
            }

            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return "unknown";
            }

            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return "unknown";

            String num = tm.getLine1Number();
            if (num == null || num.trim().isEmpty()) return "unknown";
            return num;
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String readMmsAddress(Context context, long mmsId) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    addrUri,
                    new String[]{"address", "type"},
                    null, null, null
            );
            if (c != null) {
                while (c.moveToNext()) {
                    int type = c.getInt(c.getColumnIndexOrThrow("type"));
                    // 137: from, 151: to 라는 식으로 들어옴 (단말마다 조금 다를 수 있음)
                    if (type == 137) { // from
                        String addr = c.getString(c.getColumnIndexOrThrow("address"));
                        if (addr != null && !addr.equalsIgnoreCase("insert-address-token")) {
                            return addr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public static String getMmsText(Context ctx, long mmsId) {
        Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
        Cursor c = null;
        StringBuilder sb = new StringBuilder();
        try {
            c = ctx.getContentResolver().query(partUri,
                    new String[]{"_id", "ct", "text"},
                    null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String ct = c.getString(c.getColumnIndexOrThrow("ct"));
                    if ("text/plain".equals(ct)) {
                        String text = c.getString(c.getColumnIndexOrThrow("text"));
                        if (text != null) sb.append(text);
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return sb.toString();
    }

    public static String getMmsAddress(Context ctx, long mmsId) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(addrUri,
                    new String[]{"address", "type"}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    int type = c.getInt(c.getColumnIndexOrThrow("type"));
                    // 137 = from
                    if (type == 137) {
                        String addr = c.getString(c.getColumnIndexOrThrow("address"));
                        if (addr != null && !addr.equals("insert-address-token")) {
                            return addr;
                        }
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }
}