package com.carlauncher.app.media;

import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class MediaRepository {

    public static class TrackInfo {
        public String title = "Нет воспроизведения";
        public String artist = "Bluetooth / Яндекс.Музыка";
        public String source = "Bluetooth 5.1";
        public Bitmap albumArt;
        public boolean isPlaying = false;
        public boolean hasSession = false;
    }

    public interface Listener {
        void onTrackChanged(TrackInfo info);
    }

    private static final MediaRepository INSTANCE = new MediaRepository();

    public static MediaRepository getInstance() {
        return INSTANCE;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final TrackInfo current = new TrackInfo();

    private MediaController activeController;

    private MediaRepository() {
    }

    public void addListener(Listener l) {
        listeners.add(l);
        mainHandler.post(() -> l.onTrackChanged(current));
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public TrackInfo getCurrent() {
        return current;
    }

    void updateFromController(MediaController controller, String sourceLabel) {
        this.activeController = controller;
        android.media.MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();

        if (metadata != null) {
            String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            Bitmap art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);

            if (title != null && !title.isEmpty()) current.title = title;
            current.artist = (artist != null && !artist.isEmpty()) ? artist : sourceLabel;
            current.albumArt = art;
        }
        current.source = sourceLabel;
        current.isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        current.hasSession = true;

        notifyListeners();
    }

    void clear() {
        current.title = "Нет воспроизведения";
        current.artist = "Bluetooth / Яндекс.Музыка";
        current.source = "Bluetooth 5.1";
        current.albumArt = null;
        current.isPlaying = false;
        current.hasSession = false;
        activeController = null;
        notifyListeners();
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) {
                l.onTrackChanged(current);
            }
        });
    }

    public void playPause() {
        if (activeController == null) return;
        PlaybackState state = activeController.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        if (playing) {
            activeController.getTransportControls().pause();
        } else {
            activeController.getTransportControls().play();
        }
    }

    public void next() {
        if (activeController != null) activeController.getTransportControls().skipToNext();
    }

    public void prev() {
        if (activeController != null) activeController.getTransportControls().skipToPrevious();
    }
}
