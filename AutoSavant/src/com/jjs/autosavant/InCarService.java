package com.jjs.autosavant;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;
import com.jjs.autosavant.storage.RouteStorage;

public class InCarService extends Service {
  private static final int METERS_TO_MILES = 1600;
  private static final String PREF = "InCarServicePrefs";
  private static final String TAG = "InCarService";
  private static final String LAST_LOCATION_LAT = "LastLat";
  private static final String LAST_LOCATION_LONG = "LastLong";

  private static final long TIME_BETWEEN_GPS = 3000;
  private static final long MAX_TIME_SINCE_LAST_LOCATION = 5000;
  private static final float MIN_DISTANCE_ACCURACY = 30;
  private static final long MAX_TIME_WAIT_LAST_LOCATION = 15000;
  private static final int NOTIFICATION_ID = 1;

  private Route.Builder routeBuilder;
  
  private boolean isRunning = false;
  private boolean shouldEnd = false;
  private boolean isEnded = false;
  private long lastLocationTime = 0;
  private long lastNotificationTime = 0;
  
  private final Object lockObject = new Object();
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
      new Thread(new Runnable() {       
        @Override
        public void run() {
          Log.v(TAG, "InCarService end thread.  Already ended: " + isEnded);
          if (!isEnded) {
            try {
              Thread.sleep(MAX_TIME_WAIT_LAST_LOCATION);
            } catch (InterruptedException e) {
            }
            synchronized (lockObject) {
              if (!isEnded) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                  @Override
                  public void run() {
                    Log.v(TAG, "Calling end from wait thread");
                    saveLastLocation();
                  }                
                });
              }
            }
          }
        }
      }).start();
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
    
    postNotification(routeBuilder);
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
    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_FINE);
    locationManager.requestLocationUpdates(
        TIME_BETWEEN_GPS, 1, criteria, locationListener, null);
  }

  public void postNotification(Route.Builder routeBuilder) {
    if (System.currentTimeMillis() - lastNotificationTime < 60 * 1000) {
      return;
    }
    lastNotificationTime = System.currentTimeMillis();
    double distance = calculateDistance();
    long time = System.currentTimeMillis()- routeBuilder.getStartTime();
    String content = String.format("Time: %d minutes", time / 60000);
    String body = String.format("Distance: %1.1fmiles", distance / METERS_TO_MILES);
    Notification notification = new NotificationCompat.Builder(this)
        .setSmallIcon(R.drawable.ic_stat_parking_spot)
        .setAutoCancel(true)
        .setTicker("AutoSavant Running")
        .setContentTitle(content)
        .setContentText(body)
        .setContentIntent(createPendingRouteIntent(routeBuilder.build()))
        .build();
    
    NotificationManager mNotificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(NOTIFICATION_ID, notification);
  }

  private void updateLocation(Location location) {
    Log.v(TAG, "Got Location Update: " + location.getAccuracy());
    if (isEnded) {
      Log.i(TAG, "Location Update after end!");
      return;
    }
    postNotification(routeBuilder);

    if (location.getAccuracy() < MIN_DISTANCE_ACCURACY ||
        System.currentTimeMillis() - routeBuilder.getEndTime() > MAX_TIME_WAIT_LAST_LOCATION) {
      lastLocationTime = location.getTime(); 
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
    synchronized (lockObject) {
      if (isEnded) {
        return;
      } 
      isEnded = true;
    }

    double distance = calculateDistance();
    long time = routeBuilder.getEndTime() - routeBuilder.getStartTime();
    routeBuilder.setDistance((int) distance);
    Route route = routeBuilder.build();
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

//      Intent mapIntent = new Intent(android.content.Intent.ACTION_VIEW, 
//          Uri.parse(
//              String.format("geo:0,0?q=%8f,%8f (Parking Spot)", 
//                  lastLocation.getLatitude(), lastLocation.getLongitude())));
      PendingIntent pendingIntent = createPendingRouteIntent(route);
      notification
      .setContentIntent(pendingIntent)
      .setTicker("Parking Spot Saved")
      .setContentTitle("Click to view parking spot")
      .setContentText(
          String.format("Distance: %1.1f miles\nTime: %d minutes", 
              distance / 1600f, time / 60000));
    }
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(1, notification.build());
    new RouteStorage(this).saveRoute(route);
    stopSelf();
  }

  public PendingIntent createPendingRouteIntent(Route route) {
    Intent mapIntent = new Intent(this, ShowRouteMapActivity.class);
    mapIntent.putExtra(ShowRouteMapActivity.SHOW_ROUTE_EXTRA_PROTO, route.toByteArray());
    PendingIntent pendingIntent = 
        PendingIntent.getActivity(this, 0, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    return pendingIntent;
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
