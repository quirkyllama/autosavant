package com.jjs.autosavant;

import android.graphics.Color;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;

public class ShowRouteMap {
  private final MainActivity activity;
  private final Route route;
  private GoogleMap map;
  
  private final int width;
  private final int height;
  
  public ShowRouteMap(MainActivity activity, int width, int height, Route route) {
    super();
    this.activity = activity;
    this.route = route;
    this.width = width;
    this.height = height;
  }
  
  private LatLngBounds getBounds() {
    LatLngBounds.Builder builder = LatLngBounds.builder();
    for (RoutePoint point : route.getRoutePointList()) {
      LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
      builder.include(latLng);
    }
    return builder.build();
  }

  public void show() {
    activity.setContentView(R.layout.big_map);
    map = ((MapFragment) activity.getFragmentManager().findFragmentById(R.id.bigMap)).getMap();
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(getBounds(), width, height, 40);
    map.moveCamera(cameraUpdate);
    showRoute();
  }
  
  public void hide() {
    MapFragment fragment = (MapFragment) activity.getFragmentManager().findFragmentById(R.id.bigMap);
    activity.getFragmentManager().beginTransaction().remove(fragment).commit();
  }

  private void showRoute() {
    PolylineOptions rectOptions = new PolylineOptions();
    LatLng latLng = null;
    for (RoutePoint point : route.getRoutePointList()) {
      latLng = new LatLng(point.getLatitude(), point.getLongitude());
      rectOptions.add(latLng);
    }
    rectOptions.color(Color.BLUE);
    map.addPolyline(rectOptions);
    MarkerOptions endMarker = new MarkerOptions();
    endMarker.position(latLng);
    endMarker.title("Parking Spot @ " + RouteCursorAdapter.getRouteTimeAgo(route));
    endMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_launcher));
    map.addMarker(endMarker);
  }
}
