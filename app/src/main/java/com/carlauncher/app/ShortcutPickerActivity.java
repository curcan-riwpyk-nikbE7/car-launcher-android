package com.carlauncher.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ShortcutPickerActivity extends Activity {

    public static final String EXTRA_PACKAGE_NAME = "package_name";

    static class Entry {
        String label;
        String packageName;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_shortcut_picker);

        RecyclerView rv = findViewById(R.id.rvPickerApps);
        rv.setLayoutManager(new GridLayoutManager(this, 6));

        List<Entry> apps = loadApps();
        rv.setAdapter(new PickerAdapter(apps, entry -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_PACKAGE_NAME, entry.packageName);
            setResult(RESULT_OK, result);
            finish();
        }));
    }

    private List<Entry> loadApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        List<Entry> apps = new ArrayList<>();
        for (ResolveInfo ri : resolveInfos) {
            Entry e = new Entry();
            e.label = ri.loadLabel(pm).toString();
            e.packageName = ri.activityInfo.packageName;
            try {
                e.icon = ri.activityInfo.loadIcon(pm);
            } catch (Exception ignored) {
            }
            apps.add(e);
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    static class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.VH> {

        interface OnPick {
            void onPick(Entry entry);
        }

        private final List<Entry> items;
        private final OnPick onPick;

        PickerAdapter(List<Entry> items, OnPick onPick) {
            this.items = items;
            this.onPick = onPick;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_grid, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Entry e = items.get(position);
            holder.label.setText(e.label);
            if (e.icon != null) {
                holder.icon.setImageDrawable(e.icon);
            } else {
                holder.icon.setImageResource(R.drawable.ic_apps);
            }
            holder.itemView.setOnClickListener(v -> onPick.onPick(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;

            VH(android.view.View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
            }
        }
    }
}
