package com.carlauncher.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

public class SpeedProvider {

    public interface Listener {
        void onSpeedChanged(float speedKmh, float speedMph);
    }

    private final LocationManager locationManager;
    private final Context context;
    private Listener listener;
    private boolean started = false;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (listener == null) return;
            float speedMs = location.hasSpeed() ? location.getSpeed() : 0f;
            float speedKmh = speedMs * 3.6f;
            float speedMph = speedMs * 2.23694f;
            listener.onSpeedChanged(speedKmh, speedMph);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }
    };

    public SpeedProvider(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start(Listener listener) {
        this.listener = listener;
        if (started || locationManager == null || !hasPermission()) return;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener);
                started = true;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 1000L, 1f, locationListener);
                started = true;
            }
        } catch (SecurityException ignored) {
        }
    }

    public void stop() {
        if (!started || locationManager == null) return;
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
        }
        started = false;
    }
}
