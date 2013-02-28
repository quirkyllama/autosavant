package com.jjs.autosavant;

import java.util.List;

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
import com.jjs.autosavant.MainActivity.ChooseDeviceDialogFragment;
import com.jjs.autosavant.proto.Place;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

public class ShowRouteMapActivity extends Activity {
  public static final String SHOW_ROUTE_EXTRA_PROTO = "ShowRouteProto";
  private static final String TAG = "ShowRouteMap";

  // May be null if no place known.
  private Place place;
  private Route route;
  private GoogleMap map;
  private PlaceStorage storage;
  
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
    storage = new PlaceStorage(this);
    place = storage.getPlaceForRoute(route.getRoutePoint(route.getRoutePointCount() - 1));
    setTitle(RouteCursorAdapter.getRouteTime(route));
    setContentView(R.layout.activity_show_route_map);
    RouteCursorAdapter.setupListView(findViewById(R.id.routeText), route, storage);
    map = ((MapFragment) getFragmentManager().findFragmentById(R.id.bigMap)).getMap();
    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    CameraUpdate cameraUpdate = 
        CameraUpdateFactory.newLatLngBounds(getBounds(), size.x, size.y, 50);
    map.moveCamera(cameraUpdate);
    showRoute();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_show_route_map, menu);
    MenuItem item = menu.findItem(R.id.satellite);
    item.setVisible(map.getMapType() == GoogleMap.MAP_TYPE_NORMAL);
    item.setOnMenuItemClickListener(
        new OnMenuItemClickListener() {                 
          @Override
          public boolean onMenuItemClick(MenuItem item) {
            map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            invalidateOptionsMenu();
            return true;
          }
        });
    item = menu.findItem(R.id.map);
    item.setVisible(map.getMapType() != GoogleMap.MAP_TYPE_NORMAL);
    item.setOnMenuItemClickListener(
        new OnMenuItemClickListener() {                 
          @Override
          public boolean onMenuItemClick(MenuItem item) {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            invalidateOptionsMenu();
            return true;
          }
        });
    item = menu.findItem(R.id.settings_for_place);
    item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        showSettingDialog();
        return true;
      }});
        
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
    String label = place != null ? place.getName() : "Parking Spot";
    endMarker.title(label + " @ " + RouteCursorAdapter.getRouteTimeAgo(route));
    endMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_launcher));
    endMarker.anchor(0.5f, 0.5f);
    map.addMarker(endMarker);
  }
  
  private void showSettingDialog() {
    RouteSettingsDialogFragment dialogFragment =
        new RouteSettingsDialogFragment(route, place, storage, this);
    dialogFragment.show(getFragmentManager(), "PlaceSettings");
  }

  public static class RouteSettingsDialogFragment extends DialogFragment {
    private final Route route;
    private final Activity activity;
    private Place place;
    private PlaceStorage storage;
    
    public RouteSettingsDialogFragment(Route route, Place place, PlaceStorage storage, Activity activity) {
      this.route = route;
      this.activity = activity;
      this.place = place;
      this.storage = storage;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      // Use the Builder class for convenient dialog construction
      AlertDialog.Builder builder = 
              new AlertDialog.Builder(getActivity());
      View settingsView = activity.getLayoutInflater().inflate(R.layout.place_settings, null);
      builder.setTitle("Place Settings:");
      builder.setView(settingsView);
      final EditText view = (EditText) settingsView.findViewById(R.id.placeName);
      final CheckBox ignored = (CheckBox) settingsView.findViewById(R.id.ignorePlace);
      if (place != null) {
        view.setText(place.getName());
        ignored.setChecked(place.getIgnored());
      }
      builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          dialog.dismiss();
        }
      });

      builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          Place.Builder newPlace;
          if (place != null) {
            newPlace = Place.newBuilder(place);
          } else {
            newPlace = Place.newBuilder();
            newPlace.setTime(System.currentTimeMillis());
            RoutePoint routePoint = route.getRoutePoint(route.getRoutePointCount() - 1);
            newPlace.setLatitude(routePoint.getLatitude());
            newPlace.setLongitude(routePoint.getLongitude());
          }
          newPlace.setName(view.getText().toString());
          newPlace.setIgnored(ignored.isChecked());
          storage.savePlace(newPlace.build(), place != null);
          dialog.dismiss();
        }
      });

      return builder.create();
    }
  }
}
