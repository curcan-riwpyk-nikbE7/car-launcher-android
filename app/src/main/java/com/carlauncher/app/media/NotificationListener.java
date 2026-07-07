package com.carlauncher.app.media;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "CarLauncherMedia";

    private static final String[] PRIORITY_PACKAGES = {
            "ru.yandex.music",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.music"
    };

    private MediaSessionManager mediaSessionManager;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::handleActiveSessions;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ComponentName componentName = new ComponentName(this, NotificationListener.class);
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName);
            handleActiveSessions(mediaSessionManager.getActiveSessions(componentName));
        } catch (SecurityException e) {
            Log.e(TAG, "Нет доступа к уведомлениям, включите его в настройках", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        MediaRepository.getInstance().clear();
    }

    private void handleActiveSessions(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            MediaRepository.getInstance().clear();
            return;
        }

        MediaController chosen = null;

        for (String pkg : PRIORITY_PACKAGES) {
            for (MediaController c : controllers) {
                if (pkg.equals(c.getPackageName()) && isActivelyPlaying(c)) {
                    chosen = c;
                    break;
                }
            }
            if (chosen != null) break;
        }

        if (chosen == null) {
            for (MediaController c : controllers) {
                if (isActivelyPlaying(c)) {
                    chosen = c;
                    break;
                }
            }
        }

        if (chosen == null && !controllers.isEmpty()) {
            chosen = controllers.get(0);
        }

        if (chosen != null) {
            String source = resolveSourceLabel(chosen.getPackageName());
            MediaRepository.getInstance().updateFromController(chosen, source);
        } else {
            MediaRepository.getInstance().clear();
        }
    }

    private boolean isActivelyPlaying(MediaController c) {
        PlaybackState state = c.getPlaybackState();
        MediaMetadata metadata = c.getMetadata();
        return state != null
                && state.getState() == PlaybackState.STATE_PLAYING
                && metadata != null;
    }

    private String resolveSourceLabel(String packageName) {
        if (packageName == null) return "Медиа";
        if (packageName.contains("bluetooth")) return "Bluetooth 5.1";
        if (packageName.equals("ru.yandex.music")) return "Яндекс.Музыка";
        if (packageName.equals("com.spotify.music")) return "Spotify";
        if (packageName.contains("youtube")) return "YouTube Music";
        try {
            PackageManager pm = getPackageManager();
            pm.getApplicationInfo(packageName, 0);
            return packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Bluetooth 5.1";
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }
}
