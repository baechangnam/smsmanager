package apps.kr.smsmanager.mms;


import android.net.*;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MmsNetworkHelper {
    private static final String TAG = "MmsNetworkHelper";
    private final ConnectivityManager cm;

    private ConnectivityManager.NetworkCallback heldCb; // ★ 유지

    public Network acquire(long timeoutMs) throws InterruptedException {
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        final Network[] out = new Network[1];
        heldCb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                out[0] = network;
                latch.countDown();
            }
            @Override public void onUnavailable() {
                latch.countDown();
            }
        };

        cm.requestNetwork(req, heldCb);
        boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!ok || out[0] == null) return null;
        return out[0];
    }

    public void release() {
        if (heldCb != null) {
            try { cm.unregisterNetworkCallback(heldCb); } catch (Exception ignore) {}
            heldCb = null;
        }
    }

    public MmsNetworkHelper(Context ctx) {
        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }



    public static void bindProcessTo(ConnectivityManager cm, Network n) {
        if (n == null) return;
        if (Build.VERSION.SDK_INT >= 23) {
            cm.bindProcessToNetwork(n);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(n);
        }
    }

    public static void unbindProcess(ConnectivityManager cm) {
        if (Build.VERSION.SDK_INT >= 23) {
            cm.bindProcessToNetwork(null);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(null);
        }
    }
}
