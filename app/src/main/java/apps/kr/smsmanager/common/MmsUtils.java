package apps.kr.smsmanager.common;


import static android.content.Context.TELEPHONY_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
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