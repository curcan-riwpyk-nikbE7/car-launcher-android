package com.carlauncher.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShortcutManager {

    private static final String PREFS_NAME = "car_launcher_shortcuts";
    private static final String KEY_PACKAGES = "packages";

    private static final String[] DEFAULT_PACKAGES = {
            "ru.yandex.yandexnavi",
            "com.google.android.youtube",
            "com.google.android.googlequicksearchbox",
            "com.android.fmradio"
    };

    private static final Map<String, KnownApp> KNOWN_APPS = new HashMap<>();

    static {
        KNOWN_APPS.put("ru.yandex.yandexnavi", new KnownApp("Yandex", R.drawable.ic_brand_yandex_nav));
        KNOWN_APPS.put("ru.yandex.yandexmaps", new KnownApp("Yandex", R.drawable.ic_brand_yandex_nav));
        KNOWN_APPS.put("ru.yandex.searchplugin", new KnownApp("Yandex", R.drawable.ic_brand_yandex_nav));
        KNOWN_APPS.put("com.google.android.youtube", new KnownApp("YouTube", R.drawable.ic_brand_youtube));
        KNOWN_APPS.put("com.google.android.googlequicksearchbox", new KnownApp("Google", R.drawable.ic_brand_google));
        KNOWN_APPS.put("com.android.fmradio", new KnownApp("Teyes Radio", R.drawable.ic_brand_teyes_radio));
        KNOWN_APPS.put("com.android.vending", new KnownApp("Play Маркет", R.drawable.ic_brand_play));
        KNOWN_APPS.put("com.android.chrome", new KnownApp("Chrome", R.drawable.ic_brand_chrome));
        KNOWN_APPS.put("ru.yandex.music", new KnownApp("Yandex Music", R.drawable.ic_brand_yandex_nav));
    }

    public static class KnownApp {
        public final String label;
        public final int iconRes;

        KnownApp(String label, int iconRes) {
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    public static class Shortcut {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public final int fallbackIconRes;
        public final boolean isInstalled;

        Shortcut(String packageName, String label, Drawable icon, int fallbackIconRes, boolean isInstalled) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.fallbackIconRes = fallbackIconRes;
            this.isInstalled = isInstalled;
        }
    }

    public static List<String> getShortcutPackages(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_PACKAGES, null);
        List<String> result = new ArrayList<>();
        if (stored == null) {
            for (String p : DEFAULT_PACKAGES) result.add(p);
        } else {
            result.addAll(stored);
        }
        return result;
    }

    public static void addShortcut(Context ctx, String packageName) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = new LinkedHashSet<>(getShortcutPackages(ctx));
        current.add(packageName);
        prefs.edit().putStringSet(KEY_PACKAGES, current).apply();
    }

    public static void removeShortcut(Context ctx, String packageName) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = new LinkedHashSet<>(getShortcutPackages(ctx));
        current.remove(packageName);
        prefs.edit().putStringSet(KEY_PACKAGES, current).apply();
    }

    public static void resetToDefault(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PACKAGES).apply();
    }

    public static Shortcut resolve(Context ctx, String packageName) {
        PackageManager pm = ctx.getPackageManager();
        try {
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String label = pm.getApplicationLabel(appInfo).toString();
            Drawable icon = pm.getApplicationIcon(appInfo);
            return new Shortcut(packageName, label, icon, R.drawable.ic_apps, true);
        } catch (PackageManager.NameNotFoundException e) {
            KnownApp known = KNOWN_APPS.get(packageName);
            if (known != null) {
                return new Shortcut(packageName, known.label, null, known.iconRes, false);
            }
            String shortName = packageName.contains(".")
                    ? packageName.substring(packageName.lastIndexOf('.') + 1)
                    : packageName;
            return new Shortcut(packageName, capitalize(shortName), null, R.drawable.ic_apps, false);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
