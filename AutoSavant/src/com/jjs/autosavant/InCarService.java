package com.jjs.autosavant;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import com.jjs.autosavant.proto.Place;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

public class InCarService extends Service {
  private static final int METERS_TO_MILES = 1600;
  private static final String PREF = "InCarServicePrefs";
  private static final String TAG = "InCarService";
  private static final String LAST_LOCATION_LAT = "LastLat";
  private static final String LAST_LOCATION_LONG = "LastLong";

  private static final int NOTIFICATION_ID = 1;

  private long lastNotificationTime = 0;
  private RouteTracker routeTracker;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.v(TAG, "InCarService.onCreate");
    routeTracker = new RouteTracker(
        (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE), this); 
  }

  @Override
  public int onStartCommand(Intent startIntent, int flags, int startId) {
    super.onStartCommand(startIntent, flags, startId);    
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
    String startState =
        startIntent.getStringExtra(BluetoothListener.STATE_EXTRA);
    final boolean isStart = startState.equals(BluetoothListener.CONNECT);

    Log.v(TAG, "In mode: " + isStart);
    if (isStart) {
      routeTracker.startTracking();   
    } else {
      if (!routeTracker.isRunning()) {
        Log.i(TAG,  "Got END command, but not running- ignoring");
        return Service.START_NOT_STICKY;
      } else {
        routeTracker.handleEnd();
      }
    }
    return Service.START_REDELIVER_INTENT;
  }

  @Override
  public void onDestroy() {
    routeTracker.onDestroy();    
    super.onDestroy();
  }

  public void postNotification(Route.Builder routeBuilder) {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    
    if (System.currentTimeMillis() - lastNotificationTime < 60 * 1000) {
      return;
    }
    lastNotificationTime = System.currentTimeMillis();
    double distance = calculateDistance(routeBuilder);
    long time = System.currentTimeMillis()- routeBuilder.getStartTime();
    String content = String.format("Time: %d minutes", time / 60000);
    String body = String.format("Distance: %1.1fmiles", distance / METERS_TO_MILES);
    Builder notification = new NotificationCompat.Builder(this)
        .setSmallIcon(R.drawable.ic_stat_parking_spot)
        .setAutoCancel(false)
        .setOngoing(true)
        .setTicker("AutoSavant Running")
        .setContentTitle(content)
        .setContentText(body);

    if (routeBuilder.getRoutePointCount() > 0) {
      notification.setContentIntent(createPendingRouteIntent(routeBuilder.build()));
    }

    notificationManager.notify(NOTIFICATION_ID, notification.build());
  }

  public void saveLastLocation(Route.Builder routeBuilder) {
    Log.v(TAG, "Saving Last Location: ");
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    double distance = calculateDistance(routeBuilder);
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
      notificationManager.notify(1, notification.build());
    } else {
      PlaceStorage placeStorage = new PlaceStorage(this);
      Place place = placeStorage.getPlaceForRoute(RouteUtils.getLastRoutePoint(routeBuilder));
      if (place != null && place.getIgnored()) {
        System.err.println("Ignoring parking spot @ Place: " + place.getName());
        notificationManager.cancel(NOTIFICATION_ID);
      } else {
        RoutePoint lastLocation = 
            routeBuilder.getRoutePoint(routeBuilder.getRoutePointCount() - 1);
        Editor editor = prefs.edit();

        editor.putFloat(LAST_LOCATION_LAT, (float) lastLocation.getLatitude());
        editor.putFloat(LAST_LOCATION_LONG, (float) lastLocation.getLongitude());
        editor.apply();
        String ticker = "Parking Spot Saved";
        if (place != null) {
          ticker = ticker + " @ " + place.getName();
        }
        PendingIntent pendingIntent = createPendingRouteIntent(route);
        notification
        .setContentIntent(pendingIntent)
        .setTicker(ticker)
        .setContentTitle("Click to view parking spot.")
        .setContentText(
            String.format("Distance: %1.1f miles\nTime: %d minutes", 
                distance / 1600f, time / 60000));
        notificationManager.notify(1, notification.build());
      }
      new RouteStorage(this).saveRoute(route);
    }
    stopSelf();
  }

  public PendingIntent createPendingRouteIntent(Route route) {
    RoutePoint point = RouteUtils.getLastRoutePoint(route);
    Intent intent = new Intent(Intent.ACTION_VIEW, 
        Uri.parse("http://maps.google.com/maps?daddr=" +
            point.getLatitude() + "," + point.getLongitude() + "&dirflg=w"));
    intent.setComponent(new ComponentName("com.google.android.apps.maps", 
        "com.google.android.maps.MapsActivity"));
 
    return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private double calculateDistance(Route.Builder route) {
    double distance = 0;

    Location lastLocation = null;
    for (RoutePoint routePoint : route.getRoutePointList()) {
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
