package com.carlauncher.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppDrawerActivity extends AppCompatActivity {

    static class InstalledApp {
        String label;
        String packageName;
        Drawable icon;
    }

    private final List<InstalledApp> allApps = new ArrayList<>();
    private AppDrawerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvApps);
        rv.setLayoutManager(new GridLayoutManager(this, 6));
        adapter = new AppDrawerAdapter(allApps, this::onAppClicked);
        rv.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        loadInstalledApps();
    }

    private void onAppClicked(InstalledApp app) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        allApps.clear();
        for (ResolveInfo ri : resolveInfos) {
            InstalledApp app = new InstalledApp();
            app.label = ri.loadLabel(pm).toString();
            app.packageName = ri.activityInfo.packageName;
            try {
                app.icon = ri.activityInfo.loadIcon(pm);
            } catch (Exception ignored) {
            }
            allApps.add(app);
        }

        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase()));
        adapter.filter("");
    }

    static class AppDrawerAdapter extends RecyclerView.Adapter<AppDrawerAdapter.VH> {

        interface OnAppClick {
            void onClick(InstalledApp app);
        }

        private final List<InstalledApp> source;
        private final List<InstalledApp> filtered = new ArrayList<>();
        private final OnAppClick onClick;

        AppDrawerAdapter(List<InstalledApp> source, OnAppClick onClick) {
            this.source = source;
            this.onClick = onClick;
        }

        void filter(String query) {
            filtered.clear();
            if (query == null || query.isEmpty()) {
                filtered.addAll(source);
            } else {
                String q = query.toLowerCase();
                for (InstalledApp app : source) {
                    if (app.label.toLowerCase().contains(q)) {
                        filtered.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        @androidx.annotation.NonNull
        @Override
        public VH onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_grid, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH holder, int position) {
            InstalledApp app = filtered.get(position);
            holder.label.setText(app.label);
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon);
            } else {
                holder.icon.setImageResource(R.drawable.ic_apps);
            }
            holder.itemView.setOnClickListener(v -> onClick.onClick(app));
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            android.widget.TextView label;

            VH(android.view.View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
            }
        }
    }
}
