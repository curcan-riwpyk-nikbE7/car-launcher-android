package com.carlauncher.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class AppLauncherUtil {

    public static void launch(Context ctx, AppItem item) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : item.packageCandidates) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                return;
            }
        }
        Toast.makeText(ctx, item.label + " не установлено", Toast.LENGTH_SHORT).show();
    }

    public static void launchPackage(Context ctx, String packageName) {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } else {
            Toast.makeText(ctx, "Приложение не установлено", Toast.LENGTH_SHORT).show();
        }
    }
}
