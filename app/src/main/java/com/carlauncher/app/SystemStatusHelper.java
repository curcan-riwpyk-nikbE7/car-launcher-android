package com.carlauncher.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import java.util.Calendar;
import java.util.Locale;

public class SystemStatusHelper {

    private static final String[] WEEKDAYS_RU = {
            "ВС", "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ"
    };

    public static String getFormattedDate() {
        Calendar c = Calendar.getInstance();
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
        String weekday = WEEKDAYS_RU[dow];
        return String.format(Locale.getDefault(), "%s %02d.%02d",
                weekday, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
    }

    public static boolean isWifiConnected(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public static int getCurrentVolumePercent(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 0;
        return am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
}
