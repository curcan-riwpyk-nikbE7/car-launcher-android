package com.carlauncher.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.carlauncher.app.gl.CarGLSurfaceView;
import com.carlauncher.app.media.MediaRepository;
import com.carlauncher.app.media.NotificationAccessUtil;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_SHORTCUT = 42;
    private static final int REQUEST_VOICE = 43;
    private static final int REQUEST_CAMERA_PERMISSION = 44;
    private static final int REQUEST_LOCATION_PERMISSION = 45;

    private TextView tvClockRail;
    private TextView tvTrackTitle, tvTrackArtist, tvTrackSource, tvDate, tvVolume;
    private TextView tvSpeed, tvSpeedUnit;
    private ImageView btnPlayPause, iconBluetooth, iconWifi, btnFlashlight;
    private View appsContainer, progressFill;
    private AudioManager audioManager;
    private CameraManager cameraManager;
    private GestureDetector gestureDetector;
    private String cameraId;
    private boolean flashlightOn = false;

    private CarGLSurfaceView carGlView;
    private SpeedProvider speedProvider;
    private String speedUnit = "mph";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MediaRepository.Listener mediaListener = info -> {
        if (tvTrackTitle != null) tvTrackTitle.setText(info.title);
        if (tvTrackArtist != null) tvTrackArtist.setText(info.artist);
        if (tvTrackSource != null) tvTrackSource.setText(info.source);
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(info.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateSystemStatus();
            handler.postDelayed(this, 15_000);
        }
    };

    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            updateSystemStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null && cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (Exception ignored) {
        }

        tvClockRail = findViewById(R.id.tvClockRail);
        tvTrackTitle = findViewById(R.id.tvTrackTitle);
        tvTrackArtist = findViewById(R.id.tvTrackArtist);
        tvTrackSource = findViewById(R.id.tvTrackSource);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        progressFill = findViewById(R.id.progressFill);
        tvDate = findViewById(R.id.tvDate);
        tvVolume = findViewById(R.id.tvVolume);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit);
        iconBluetooth = findViewById(R.id.iconBluetooth);
        iconWifi = findViewById(R.id.iconWifi);
        btnFlashlight = findViewById(R.id.btnFlashlight);
        appsContainer = findViewById(R.id.appsContainer);

        setupCarGlView();
        setupSpeedProvider();

        findViewById(R.id.btnPrev).setOnClickListener(v -> MediaRepository.getInstance().prev());
        findViewById(R.id.btnNext).setOnClickListener(v -> MediaRepository.getInstance().next());
        btnPlayPause.setOnClickListener(v -> MediaRepository.getInstance().playPause());

        findViewById(R.id.btnSettingsRail).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnCubeRail).setOnClickListener(v ->
                startActivity(new Intent(this, AppDrawerActivity.class)));

        findViewById(R.id.btnAssistant).setOnClickListener(v -> launchVoiceAssistant());

        findViewById(R.id.btnNavigationRail).setOnClickListener(v -> launchNavigation());

        findViewById(R.id.btnCarSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        if (cameraId != null) {
            btnFlashlight.setOnClickListener(v -> toggleFlashlight());
        } else {
            btnFlashlight.setAlpha(0.3f);
        }

        View radioCard = findViewById(R.id.radioCard);
        radioCard.setOnClickListener(v -> AppLauncherUtil.launchPackage(this, "com.android.fmradio"));

        setupGestures();
        rebuildShortcuts();

        speedUnit = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(SettingsActivity.KEY_SPEED_UNIT, "mph");
        updateSpeedUnitLabel();

        updateClock();
        updateSystemStatus();
        maybeRequestNotificationAccess();
    }

    private void setupCarGlView() {
        FrameLayout container = findViewById(R.id.carGlContainer);
        if (container == null) return;
        carGlView = new CarGLSurfaceView(this);
        container.addView(carGlView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void setupSpeedProvider() {
        speedProvider = new SpeedProvider(this);
        if (!speedProvider.hasPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    @SuppressLint("SetTextI18n")
    private void onSpeedChanged(float speedKmh, float speedMph) {
        float displaySpeed = speedUnit.equals("mph") ? speedMph : speedKmh;
        int rounded = Math.round(displaySpeed);
        if (tvSpeed != null) tvSpeed.setText(String.valueOf(rounded));
        if (carGlView != null) carGlView.setSpeed(displaySpeed);
    }

    private void updateSpeedUnitLabel() {
        if (tvSpeedUnit != null) {
            tvSpeedUnit.setText(speedUnit.equals("mph") ? "MPH" : "KM/H");
        }
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 70;
            private static final int SWIPE_VELOCITY_THRESHOLD = 60;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            MediaRepository.getInstance().prev();
                            Toast.makeText(MainActivity.this, "⏮ Предыдущий трек", Toast.LENGTH_SHORT).show();
                        } else {
                            MediaRepository.getInstance().next();
                            Toast.makeText(MainActivity.this, "⏭ Следующий трек", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            adjustVolume(-1);
                        } else {
                            adjustVolume(1);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void adjustVolume(int direction) {
        if (audioManager == null) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int next = Math.max(0, Math.min(max, current + direction));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI);
        updateSystemStatus();
    }

    private void toggleFlashlight() {
        if (cameraManager == null || cameraId == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        try {
            flashlightOn = !flashlightOn;
            cameraManager.setTorchMode(cameraId, flashlightOn);
            btnFlashlight.setColorFilter(flashlightOn
                    ? getColor(R.color.accent_cyan)
                    : getColor(R.color.text_secondary));
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Фонарик недоступен на этом устройстве", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleFlashlight();
        }
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            speedProvider.start(this::onSpeedChanged);
        }
    }

    private void launchVoiceAssistant() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите команду...");
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            try {
                Intent assist = new Intent(Intent.ACTION_VOICE_COMMAND);
                assist.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(assist);
            } catch (Exception ex) {
                Toast.makeText(this, "Голосовой ассистент недоступен", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchNavigation() {
        String[] navPackages = {"ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.google.android.apps.maps"};
        for (String pkg : navPackages) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }
        }
        Toast.makeText(this, "Установите Яндекс.Навигатор или Google Карты", Toast.LENGTH_SHORT).show();
    }

    private void rebuildShortcuts() {
        android.widget.LinearLayout container = (android.widget.LinearLayout) appsContainer;
        container.removeAllViews();

        List<String> packages = ShortcutManager.getShortcutPackages(this);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String pkg : packages) {
            View tile = inflater.inflate(R.layout.item_app_tile, container, false);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginEnd(dp(6));
            tile.setLayoutParams(lp);

            ShortcutManager.Shortcut shortcut = ShortcutManager.resolve(this, pkg);
            ImageView icon = tile.findViewById(R.id.appIcon);
            TextView label = tile.findViewById(R.id.appLabel);

            if (shortcut.icon != null) {
                icon.setImageDrawable(shortcut.icon);
            } else {
                icon.setImageResource(shortcut.fallbackIconRes);
            }
            label.setText(shortcut.label);

            final String pkgFinal = pkg;
            tile.setOnClickListener(v -> {
                if (shortcut.isInstalled) {
                    AppLauncherUtil.launchPackage(this, pkgFinal);
                } else {
                    Toast.makeText(this, shortcut.label + " не установлено на этом устройстве",
                            Toast.LENGTH_SHORT).show();
                }
            });
            tile.setOnLongClickListener(v -> {
                showRemoveShortcutDialog(pkg, shortcut.label);
                return true;
            });

            container.addView(tile);
        }

        View addTile = inflater.inflate(R.layout.item_app_tile_add, container, false);
        android.widget.LinearLayout.LayoutParams addLp = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        addTile.setLayoutParams(addLp);
        addTile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ShortcutPickerActivity.class);
            startActivityForResult(intent, REQUEST_PICK_SHORTCUT);
        });
        container.addView(addTile);
    }

    private void showRemoveShortcutDialog(String pkg, String label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage("Убрать этот ярлык с рабочего стола?")
                .setPositiveButton("Убрать", (d, w) -> {
                    ShortcutManager.removeShortcut(this, pkg);
                    rebuildShortcuts();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_SHORTCUT && resultCode == Activity.RESULT_OK && data != null) {
            String pkg = data.getStringExtra(ShortcutPickerActivity.EXTRA_PACKAGE_NAME);
            if (pkg != null) {
                ShortcutManager.addShortcut(this, pkg);
                rebuildShortcuts();
                Toast.makeText(this, "Ярлык добавлен", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void maybeRequestNotificationAccess() {
        if (NotificationAccessUtil.isNotificationAccessGranted(this)) return;

        new AlertDialog.Builder(this)
                .setTitle("Доступ к медиа")
                .setMessage("Чтобы лаунчер показывал музыку с Bluetooth и Яндекс.Музыки, " +
                        "включите доступ к уведомлениям для \"Car Launcher\" на следующем экране.")
                .setCancelable(true)
                .setPositiveButton("Открыть настройки", (d, w) ->
                        NotificationAccessUtil.openNotificationAccessSettings(this))
                .setNegativeButton("Позже", null)
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void updateClock() {
        Calendar c = Calendar.getInstance();
        String time = String.format(Locale.getDefault(), "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        if (tvClockRail != null) tvClockRail.setText(time);
    }

    @SuppressLint("SetTextI18n")
    private void updateSystemStatus() {
        if (tvDate != null) {
            tvDate.setText(SystemStatusHelper.getFormattedDate());
        }
        if (tvVolume != null) {
            tvVolume.setText(String.valueOf(SystemStatusHelper.getCurrentVolumePercent(this)));
        }
        if (iconBluetooth != null) {
            boolean btOn = SystemStatusHelper.isBluetoothEnabled();
            iconBluetooth.setImageResource(btOn ? R.drawable.ic_bluetooth : R.drawable.ic_bluetooth_off);
            iconBluetooth.setAlpha(btOn ? 1f : 0.4f);
        }
        if (iconWifi != null) {
            boolean wifiOn = SystemStatusHelper.isWifiConnected(this);
            iconWifi.setAlpha(wifiOn ? 1f : 0.4f);
        }
        if (progressFill != null) {
            boolean playing = MediaRepository.getInstance().getCurrent().isPlaying;
            progressFill.setAlpha(playing ? 1f : 0.4f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(systemReceiver, filter);
        handler.post(clockTick);
        MediaRepository.getInstance().addListener(mediaListener);
        rebuildShortcuts();
        updateSystemStatus();
        if (carGlView != null) carGlView.onResume();
        if (speedProvider != null && speedProvider.hasPermission()) {
            speedProvider.start(this::onSpeedChanged);
        }

        speedUnit = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(SettingsActivity.KEY_SPEED_UNIT, "mph");
        updateSpeedUnitLabel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        handler.removeCallbacks(clockTick);
        MediaRepository.getInstance().removeListener(mediaListener);
        if (carGlView != null) carGlView.onPause();
        if (speedProvider != null) speedProvider.stop();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public void onBackPressed() {
        // Лаунчер: игнорируем кнопку "назад", чтобы остаться домашним экраном
    }
}
