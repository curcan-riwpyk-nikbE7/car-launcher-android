package com.carlauncher.app;

public class AppItem {
    public final String label;
    public final int iconRes;
    public final String[] packageCandidates;

    public AppItem(String label, int iconRes, String... packageCandidates) {
        this.label = label;
        this.iconRes = iconRes;
        this.packageCandidates = packageCandidates;
    }
}
