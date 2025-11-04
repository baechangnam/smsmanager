// MmsWapPushReceiver.java
package apps.kr.smsmanager.mms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MmsWapPushReceiver extends BroadcastReceiver {
    private static final String TAG = "MmsWapPushReceiver";
    private static final String MMS_MIME = "application/vnd.wap.mms-message";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!MMS_MIME.equals(intent.getType())) return;

        byte[] data = intent.getByteArrayExtra("data");
        if (data == null) return;

        // ↓↓↓ AOSP Mms PDU parser 씀
        apps.kr.smsmanager.mms.pdu.GenericPdu pdu =
                new apps.kr.smsmanager.mms.pdu.PduParser(data).parse();
        if (pdu == null) return;

        int type = pdu.getMessageType();
        if (type == apps.kr.smsmanager.mms.pdu.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            apps.kr.smsmanager.mms.pdu.NotificationInd ni =
                    (apps.kr.smsmanager.mms.pdu.NotificationInd) pdu;

            byte[] cl = ni.getContentLocation();
            String contentLocation = (cl != null) ? new String(cl) : null;

            byte[] tid = ni.getTransactionId();
            String transactionId = (tid != null) ? new String(tid) : null;

            Log.d(TAG, "notif: loc=" + contentLocation + ", tx=" + transactionId);

            Intent svc = new Intent(context, MmsDownloadService.class);
            svc.putExtra("contentLocation", contentLocation);
            svc.putExtra("transactionId", transactionId);
            context.startService(svc);
        }
    }
}
