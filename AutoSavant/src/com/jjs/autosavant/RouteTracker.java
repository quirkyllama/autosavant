package com.jjs.autosavant;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jjs.autosavant.proto.Route;

public class RouteTracker implements LocationListener {
  private static final String TAG = "RouteTracker";
  private static final long TIME_BETWEEN_GPS = 3000;
  private static final long MAX_TIME_SINCE_LAST_LOCATION = 5000;
  private static final float MIN_DISTANCE_ACCURACY = 30;
  private static final long MAX_TIME_WAIT_LAST_LOCATION = 15000;

  private final Object lockObject = new Object();
  private final LocationManager locationManager;
  private final InCarService service;
  
  private final Route.Builder routeBuilder;
  private boolean isRunning = false;
  private boolean shouldEnd = false;
  private boolean isEnded = false;
  private long lastLocationTime = 0;
  
  public RouteTracker(LocationManager locationManager, InCarService service) {
    this.locationManager = locationManager;
    this.service = service;
    routeBuilder = Route.newBuilder();
  }

  public void startTracking() {
    shouldEnd = false;
    isEnded = false;
    isRunning = true;
    routeBuilder.setStartTime(System.currentTimeMillis());
    Log.v(TAG, "Handle Location startxxx");
    
    service.postNotification(routeBuilder);
 
    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_FINE);
    locationManager.requestLocationUpdates(
        TIME_BETWEEN_GPS, 1, criteria, this, null);
  }
  
  @Override
  public void onLocationChanged(Location location) {
    Log.v(TAG, "Got Location Update: " + location.getAccuracy());
    if (isEnded) {
      Log.i(TAG, "Location Update after end!");
      return;
    }
    service.postNotification(routeBuilder);

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

    service.saveLastLocation(routeBuilder);
  }
  
  @Override
  public void onProviderDisabled(String arg0) {}

  @Override
  public void onProviderEnabled(String arg0) {}

  @Override
  public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
  
  public void handleEnd() {
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

 
  public void onDestroy() {
    Log.w(TAG, "Destroyed!");
    locationManager.removeUpdates(this);
  }

  public boolean isRunning() {
    return isRunning;
  }
}
