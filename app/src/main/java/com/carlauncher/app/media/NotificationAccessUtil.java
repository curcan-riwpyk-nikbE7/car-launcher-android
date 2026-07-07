package com.carlauncher.app.media;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public class NotificationAccessUtil {

    public static boolean isNotificationAccessGranted(Context context) {
        String enabledListeners = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabledListeners)) return false;
        return enabledListeners.contains(context.getPackageName());
    }

    public static void openNotificationAccessSettings(Context context) {
        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
