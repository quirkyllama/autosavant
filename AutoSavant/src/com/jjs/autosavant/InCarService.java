package com.jjs.autosavant;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;

public class InCarService extends Service {
  private static final String PREF = "InCarServicePrefs";
  private static final String TAG = "InCarService";
  private static final String LAST_LOCATION_LAT = "LastLat";
  private static final String LAST_LOCATION_LONG = "LastLong";

  private static final long TIME_BETWEEN_GPS = 15000;
  private static final long MAX_TIME_SINCE_LAST_LOCATION = 5000;
  private static final float MIN_DISTANCE_ACCURACY = 15;

  private Route.Builder routeBuilder;
  
  private boolean isRunning = false;
  private boolean shouldEnd = false;
  private boolean isEnded = false;
  private long lastLocationTime = 0;
  
  private LocationManager locationManager;
  private LocationListener locationListener;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @Override
  public int onStartCommand(Intent startIntent, int flags, int startId) {
    super.onStartCommand(startIntent, flags, startId);
    locationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
    
    Log.v(TAG, "Starting InCarService: " + startIntent);
    if (startIntent == null) {
      // WTF is this?
      return Service.START_NOT_STICKY;
    }
    if (!startIntent.hasExtra(BluetoothListener.STATE_EXTRA)) {
      Log.v(TAG, "Intent with no STATE_EXTRA");
      stopSelf();
      return Service.START_NOT_STICKY;
    }
    shouldEnd = false;
    isEnded = false;
    String startState =
        startIntent.getStringExtra(BluetoothListener.STATE_EXTRA);
    final boolean isStart = startState.equals(BluetoothListener.CONNECT);

    Log.v(TAG, "In mode: " + isStart);
    if (isStart) {
      handleStart();
    } else {
      if (!isRunning) {
        Log.i(TAG,  "Got END command, but not running- ignoring");
        return Service.START_NOT_STICKY;
      } else {
        handleEnd();
      }
    }
    return Service.START_REDELIVER_INTENT;
  }

  protected void handleEnd() {
    routeBuilder.setEndTime(System.currentTimeMillis());
    long timeSinceLocation = 
        System.currentTimeMillis() - lastLocationTime;
    if (timeSinceLocation < MAX_TIME_SINCE_LAST_LOCATION) {
      saveLastLocation();
    } else {
      shouldEnd = true;
    }
  }

  @Override
  public void onDestroy() {
    Log.w(TAG, "InCarService destroyed!");
    if (locationListener != null) {
      System.err.println("Removing location listener");
      locationManager.removeUpdates(locationListener);
    }
    
    super.onDestroy();
  }

  protected void handleStart() {
    isRunning = true;
    routeBuilder = Route.newBuilder();
    routeBuilder.setStartTime(System.currentTimeMillis());
    Log.v(TAG, "Handle Location startxxx");
    
    Notification notification = new NotificationCompat.Builder(this)
    .setSmallIcon(R.drawable.ic_stat_parking_spot)
    .setAutoCancel(true)
    .setTicker("AutoSavant Running")
    .setContentTitle("AutoSavant Running")
    .build();
    NotificationManager mNotificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(1, notification);
    locationListener = new LocationListener() {
      @Override
      public void onLocationChanged(Location location) {
        updateLocation(location);
      }
      
      @Override
      public void onProviderEnabled(String provider) {}
      
      @Override
      public void onProviderDisabled(String provider) {}

      @Override
      public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      }
    }; 
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 
        TIME_BETWEEN_GPS, 0, locationListener);
  }

  private void updateLocation(Location location) {
    Log.v(TAG, "Got Location Update: " + location.getAccuracy());
    if (isEnded) {
      Log.i(TAG, "Location Update after end!");
      return;
    }
    if (location.getAccuracy() < MIN_DISTANCE_ACCURACY) {
      lastLocationTime = location.getTime();
      RoutePoint routePoint = 
          routeBuilder.addRoutePointBuilder()
          .setTime(location.getTime())
          .setLatitude((float) location.getLatitude())
          .setLongitude((float) location.getLongitude()).build();

      if (shouldEnd) {
        saveLastLocation();
      }
    }
  }

  private void saveLastLocation() {
    Log.v(TAG, "Saving Last Location: " + isEnded);
    if (isEnded) {
      return;
    } 
    double distance = calculateDistance();
    long time = routeBuilder.getEndTime() - routeBuilder.getStartTime();
    isEnded = true;
    SharedPreferences prefs = 
        getSharedPreferences(PREF, Context.MODE_PRIVATE);
    Builder notification = new NotificationCompat.Builder(this)
    .setSmallIcon(R.drawable.ic_stat_parking_spot)
    .setAutoCancel(true);

    if (routeBuilder.getRoutePointCount() == 0) {
      notification.setContentTitle("No parking spot saved!");
    } else {
      RoutePoint lastLocation = 
          routeBuilder.getRoutePoint(routeBuilder.getRoutePointCount() - 1);
      Editor editor = prefs.edit();

      editor.putFloat(LAST_LOCATION_LAT, (float) lastLocation.getLatitude());
      editor.putFloat(LAST_LOCATION_LONG, (float) lastLocation.getLongitude());
      editor.apply();

      Intent mapIntent = new Intent(android.content.Intent.ACTION_VIEW, 
          Uri.parse(
              String.format("geo:0,0?q=%8f,%8f (Parking Spot)", 
                  lastLocation.getLatitude(), lastLocation.getLongitude())));

      PendingIntent pendingIntent = 
          PendingIntent.getActivity(this, 0, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      notification
      .setContentIntent(pendingIntent)
      .setTicker("Parking Spot Saved")
      .setContentTitle("View parking spot")
      .setContentText(String.format("Distance Driven: %1.1f miles\nTime: %d minutes", 
          distance / 1600f, time / 60000));
    }
    NotificationManager mNotificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(1, notification.build());
    stopSelf();
  }

  private double calculateDistance() {
    double distance = 0;

    Location lastLocation = null;
    for (RoutePoint routePoint : routeBuilder.getRoutePointList()) {
      Location location = createLocationFromRoutePoint(routePoint); 
      if (lastLocation != null) {
        distance += lastLocation.distanceTo(location);
      }
      lastLocation = location;
    }
    return distance;
  }

  private Location createLocationFromRoutePoint(RoutePoint routePoint) {
    Location location = new Location("fake");
    location.setLatitude(routePoint.getLatitude());
    location.setLongitude(routePoint.getLongitude());
    return location;
  }
}
