package com.carlauncher.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carlauncher.app.media.NotificationAccessUtil;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "car_launcher_prefs";
    public static final String KEY_SPEED_UNIT = "speed_unit";
    public static final String KEY_THEME = "theme";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        setupRow(R.id.rowNotificationAccess, R.drawable.ic_notifications,
                "Доступ к уведомлениям",
                NotificationAccessUtil.isNotificationAccessGranted(this)
                        ? "Разрешено — музыка с Bluetooth/Яндекс.Музыки видна"
                        : "Нужен для показа музыки с Bluetooth и Яндекс.Музыки",
                null, true);

        setupRow(R.id.rowLocationAccess, R.drawable.ic_gps,
                "Доступ к геолокации",
                "Нужен для реальной скорости с GPS (вращение колёс 3D-машины)",
                null, true);

        setupRow(R.id.rowTheme, R.drawable.ic_palette,
                "Тема оформления",
                "Цветовая схема рабочего стола",
                themeLabel(prefs.getString(KEY_THEME, "neon")), true);

        setupRow(R.id.rowWallpaper, R.drawable.ic_light,
                "Обои рабочего стола",
                "Изменить фоновое изображение",
                null, true);

        setupRow(R.id.rowSpeedUnit, R.drawable.ic_gps,
                "Единицы скорости",
                "MPH или км/ч, влияет на GPS-спидометр",
                prefs.getString(KEY_SPEED_UNIT, "mph").equals("mph") ? "MPH" : "км/ч", true);

        setupRow(R.id.rowManageApps, R.drawable.ic_apps,
                "Управление приложениями",
                "Добавить или убрать ярлыки на главном экране",
                null, true);

        setupRow(R.id.rowResetLayout, R.drawable.ic_close,
                "Сбросить рабочий стол",
                "Вернуть расположение ярлыков по умолчанию",
                null, true);

        setupRow(R.id.rowAbout, R.drawable.ic_info,
                "О программе",
                "Car Launcher v1.0 — для автомагнитол на Android 8.0+",
                null, false);

        bindClicks();
    }

    private void bindClicks() {
        findViewById(R.id.rowNotificationAccess).setOnClickListener(v ->
                NotificationAccessUtil.openNotificationAccessSettings(this));

        findViewById(R.id.rowLocationAccess).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        findViewById(R.id.rowTheme).setOnClickListener(v -> showThemePicker());

        findViewById(R.id.rowWallpaper).setOnClickListener(v ->
                Toast.makeText(this, "Выбор обоев (демо)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.rowSpeedUnit).setOnClickListener(v -> toggleSpeedUnit());

        findViewById(R.id.rowManageApps).setOnClickListener(v ->
                startActivity(new Intent(this, AppDrawerActivity.class)));

        findViewById(R.id.rowResetLayout).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Сбросить рабочий стол?")
                        .setMessage("Ярлыки на главном экране вернутся к значениям по умолчанию.")
                        .setPositiveButton("Сбросить", (d, w) -> {
                            ShortcutManager.resetToDefault(this);
                            Toast.makeText(this, "Рабочий стол сброшен", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Отмена", null)
                        .show());

        findViewById(R.id.rowAbout).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Car Launcher")
                        .setMessage("Версия 1.0\n\nЛаунчер для автомагнитол на Android 8.0+ " +
                                "с захватом музыки Bluetooth и Яндекс.Музыки через MediaSession API.\n\n" +
                                "Спидометр и вращение колёс 3D-машины используют реальную скорость с GPS.\n\n" +
                                "Жесты: свайп влево/вправо — переключение трека, вверх/вниз — громкость.")
                        .setPositiveButton("Ок", null)
                        .show());
    }

    private void showThemePicker() {
        String[] themes = {"Неон (по умолчанию)", "Тёмная", "Классическая"};
        String[] values = {"neon", "dark", "classic"};
        new AlertDialog.Builder(this)
                .setTitle("Выберите тему")
                .setItems(themes, (dialog, which) -> {
                    prefs.edit().putString(KEY_THEME, values[which]).apply();
                    TextView value = findViewById(R.id.rowTheme).findViewById(R.id.rowValue);
                    value.setText(themes[which]);
                    Toast.makeText(this, "Тема применится после перезапуска", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleSpeedUnit() {
        String current = prefs.getString(KEY_SPEED_UNIT, "mph");
        String next = current.equals("mph") ? "kmh" : "mph";
        prefs.edit().putString(KEY_SPEED_UNIT, next).apply();
        TextView value = findViewById(R.id.rowSpeedUnit).findViewById(R.id.rowValue);
        value.setText(next.equals("mph") ? "MPH" : "км/ч");
    }

    private String themeLabel(String value) {
        switch (value) {
            case "dark": return "Тёмная";
            case "classic": return "Классическая";
            default: return "Неон";
        }
    }

    private void setupRow(int rowId, int iconRes, String title, String subtitle, String value, boolean showChevron) {
        View row = findViewById(rowId);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView tvTitle = row.findViewById(R.id.rowTitle);
        TextView tvSubtitle = row.findViewById(R.id.rowSubtitle);
        TextView tvValue = row.findViewById(R.id.rowValue);
        ImageView chevron = row.findViewById(R.id.rowChevron);

        icon.setImageResource(iconRes);
        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);
        tvValue.setText(value == null ? "" : value);
        chevron.setVisibility(showChevron ? View.VISIBLE : View.GONE);
    }
}
