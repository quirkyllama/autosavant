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
  private static final float MIN_DISTANCE_ACCURACY = 20;
  private static final long MAX_TIME_WAIT_LAST_LOCATION = 15000;
  private static final long WAIT_BEFORE_END_MILLIS = 120 * 1000;
  private static final long MIN_ROUTE_TIME = 2 * 60 * 1000;
  private static final long MIN_ROUTE_DISTANCE = 300;  
  private static final int MIN_ROUTE_POINTS = 10;
  
  private enum State {
    NONE,
    RUNNING,
    WAIT_FOR_LAST_LOCATION,
    WAIT_FOR_END,   
  }
  private final Object lockObject = new Object();
  private final LocationManager locationManager;
  private final InCarService service;
  private final Route.Builder routeBuilder;

  private State state;
  private long lastLocationTime = 0;
  
  public RouteTracker(LocationManager locationManager, InCarService service) {
    this.locationManager = locationManager;
    this.service = service;
    routeBuilder = Route.newBuilder();
    state = State.NONE;
  }

  public void startTracking() {
    switch (state) {
      case RUNNING: return;
      case WAIT_FOR_LAST_LOCATION: 
      case WAIT_FOR_END: 
        Log.v(TAG, "Restarting while waiting for last location!");
        break;
      case NONE: 
        Log.v(TAG, "Handle Location startxxx");
        routeBuilder.setStartTime(System.currentTimeMillis());
        break;
    }
    updateState(State.RUNNING);
    service.postNotification(routeBuilder);
 
    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_FINE);
    locationManager.requestLocationUpdates(
        TIME_BETWEEN_GPS, 1, criteria, this, null);
  }
  
  @Override
  public void onLocationChanged(Location location) {
    Log.v(TAG, "Got Location Update: " + location.getAccuracy());
    switch (state) {
      case RUNNING:
      case WAIT_FOR_LAST_LOCATION: 
      break;
 
      case NONE:
      case WAIT_FOR_END:
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

      if (state == State.WAIT_FOR_LAST_LOCATION) {
        updateState(State.WAIT_FOR_END);
      }
    }
  }

  private void saveLastLocation() {
    Log.v(TAG, "Saving Last Location: " + state);
    synchronized (lockObject) {
      if (state == State.NONE) {
        return;
      }
      updateState(State.NONE);
    }

    service.saveLastLocation(routeBuilder);
  }
  
  @Override
  public void onProviderDisabled(String arg0) {}

  @Override
  public void onProviderEnabled(String arg0) {}

  @Override
  public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
  
  private void updateState(State newState) {
    Log.v(TAG, "State transition: " + state + " ->" + newState);
    state = newState;
  }
  public void handleEnd() {
    if (state != State.RUNNING) {
      Log.v(TAG, "Ended but not running!");
      return;
    }
    routeBuilder.setEndTime(System.currentTimeMillis());   

    if (routeBuilder.getEndTime() - routeBuilder.getStartTime() < MIN_ROUTE_TIME ||
        routeBuilder.getRoutePointCount() < MIN_ROUTE_POINTS ||
        RouteUtils.getDistance(routeBuilder.getRoutePoint(0),
            RouteUtils.getLastRoutePoint(routeBuilder)) < MIN_ROUTE_DISTANCE) {
      Log.v(TAG, "Route too short: " + routeBuilder.getRoutePointCount());
      locationManager.removeUpdates(this);
      updateState(State.NONE);
    }

    long timeSinceLocation = 
        System.currentTimeMillis() - lastLocationTime;
    if (timeSinceLocation < MAX_TIME_SINCE_LAST_LOCATION) {
      locationManager.removeUpdates(this);
      updateState(State.WAIT_FOR_END);
    } else {
      updateState(State.WAIT_FOR_LAST_LOCATION);
    }
    
    new Thread(new Runnable() {       
      @Override
      public void run() {
        waitForEndTime();
      }
    }).start();
  }

  public void onDestroy() {
    Log.w(TAG, "Destroyed!");
    locationManager.removeUpdates(this);
  }

  public boolean isRunning() {
    return state == State.RUNNING;
  }

  public void waitForEndTime() {
    try {
      Thread.sleep(WAIT_BEFORE_END_MILLIS);
    } catch (InterruptedException e) {
    }

    synchronized (lockObject) {
      if (state == State.RUNNING) {
        Log.v(TAG, "Restarting!");
        return;
      }
      
      locationManager.removeUpdates(this);
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
