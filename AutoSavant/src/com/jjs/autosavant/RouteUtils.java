package com.jjs.autosavant;

import android.location.Location;

import com.jjs.autosavant.proto.RouteOrBuilder;
import com.jjs.autosavant.proto.RoutePoint;

public class RouteUtils {

  public static RoutePoint getLastRoutePoint(RouteOrBuilder route) {
    return route.getRoutePoint(route.getRoutePointCount() - 1);
  }
  
  public static Location getLocationForRoutePoint(RoutePoint routePoint) {
    Location location = new Location("");
    location.setLatitude(routePoint.getLatitude());
    location.setLongitude(routePoint.getLongitude());
    return location;
  }
  
  public static double getDistance(RoutePoint p1, RoutePoint p2) {
    Location l1 = getLocationForRoutePoint(p1);
    Location l2 = getLocationForRoutePoint(p2);
    return l1.distanceTo(l2);
  }
}
