package com.jjs.autosavant;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;

import android.os.Bundle;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import android.view.Menu;

public class ShowRouteMapActivity extends Activity {
  public static final String SHOW_ROUTE_EXTRA_PROTO = "ShowRouteProto";
  private static final String TAG = "ShowRouteMap";

  private Route route;
  private GoogleMap map;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    byte[] data = getIntent().getExtras().getByteArray(SHOW_ROUTE_EXTRA_PROTO);
    if (data == null) {
      Log.v(TAG, "Intent Data is null");
      finish();
    }
    try {
      route = Route.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      Log.v(TAG, "Intent Data does not parse");
      e.printStackTrace();
      finish();
    }
    
    setContentView(R.layout.activity_show_route_map);
    RouteCursorAdapter.setupListView(findViewById(R.id.routeText), route);
    map = ((MapFragment) getFragmentManager().findFragmentById(R.id.bigMap)).getMap();
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    System.err.println("Showing map");
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    CameraUpdate cameraUpdate = 
        CameraUpdateFactory.newLatLngBounds(getBounds(), size.x, size.y, 50);
    map.moveCamera(cameraUpdate);
    showRoute();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.activity_show_route_map, menu);
    return true;
  }

  private LatLngBounds getBounds() {
    LatLngBounds.Builder builder = LatLngBounds.builder();
    for (RoutePoint point : route.getRoutePointList()) {
      LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
      builder.include(latLng);
    }
    return builder.build();
  }

  public void hide() {
    MapFragment fragment = (MapFragment) getFragmentManager().findFragmentById(R.id.bigMap);
    getFragmentManager().beginTransaction().remove(fragment).commit();
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
    endMarker.anchor(0.5f, 0.5f);
    map.addMarker(endMarker);
  }
}
