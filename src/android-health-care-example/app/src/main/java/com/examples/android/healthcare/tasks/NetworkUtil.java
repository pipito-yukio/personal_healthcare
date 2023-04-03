package com.examples.android.healthcare.tasks;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.examples.android.healthcare.constants.RequestDevice;

/**
 Active Netowrk connectivity judgment utility.
*/
public class NetworkUtil {
    private static final String TAG = "NetworkUtil";

    private NetworkUtil() {}

    public static RequestDevice getActiveNetworkDevice(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isWifiConnect = false;
        boolean isMoblieConnect = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P/* Pie(28) */) {
            Log.d(TAG, "Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
            // https://developer.android.com/training/basics/network-ops/reading-network-state?hl=ja
            Network currentNetwork = manager.getActiveNetwork();
            NetworkCapabilities caps = manager.getNetworkCapabilities(currentNetwork);
//            Log.d(TAG, "NetworkCapabilities: " + caps);
            isWifiConnect = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            isMoblieConnect = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            // 'getType()' is deprecated as of API 28: Android 9.0 (Pie)
            for (Network network : manager.getAllNetworks()) {
//                Log.d(TAG, "Network: " + network);
                NetworkInfo info = manager.getNetworkInfo(network);
                Log.d(TAG, "NetworkInfo: " + info);
                if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                    isWifiConnect |= info.isConnected();
                }
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    isMoblieConnect |= info.isConnected();
                }
            }
        }
        Log.d(TAG, "isWifiConnect: " + isWifiConnect);
        Log.d(TAG, "isMobileConnect: " + isMoblieConnect);
        if (isWifiConnect) {
            return RequestDevice.WIFI;
        }
        if (isMoblieConnect) {
            return RequestDevice.MOBILE;
        }
        return RequestDevice.NONE;
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        Log.d(TAG, "NetworkInfo: " + info);
        return (info != null && info.isConnected());
    }
}
