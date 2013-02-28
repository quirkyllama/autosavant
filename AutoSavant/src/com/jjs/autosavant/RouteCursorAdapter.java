package com.jjs.autosavant;

import com.jjs.autosavant.R;
import com.jjs.autosavant.proto.Place;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class RouteCursorAdapter extends CursorAdapter {
  private static final long MILLIS_PER_MINUTE = 60 * 1000;
  private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
  private static final String DATE_FORMAT = "E, MMMM dd, yyyy h:mmaa";
  private static final DateFormat dateFormat = new DateFormat();
  private final RouteStorage storage;
  private final PlaceStorage placeStorage;
  private final RouteClickListener listener;
  
  public interface RouteClickListener {
    public void onClick(Route route);
  }
  
  public RouteCursorAdapter(Context context, RouteStorage storage, PlaceStorage placeStorage, RouteClickListener listener) {
    super(context, storage.getRouteCursor(), FLAG_REGISTER_CONTENT_OBSERVER);
    this.storage = storage;
    this.listener = listener;
    this.placeStorage = placeStorage;
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    final Route route = storage.parseFromCursor(cursor);
    setupListView(view, route, placeStorage);
    view.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        System.err.println("Click on route");
        listener.onClick(route);
      }
    });
  }

  public static void setupListView(View view, final Route route, PlaceStorage placeStorage) {
    Place place = placeStorage.getPlaceForRoute(route.getRoutePoint(route.getRoutePointCount() - 1));
    TextView dateView = (TextView) view.findViewById(R.id.routeListDate); 
    String dateString = getRouteTimeAgo(route);
    if (place != null) {
      dateString = "To: " + place.getName() + dateString;
    }
    
    dateView.setText(dateString);
    TextView distanceView = (TextView) view.findViewById(R.id.routeListDistance);
    String distance = getRouteDistance(route);
    distanceView.setText(distance);

    TextView timeView = (TextView) view.findViewById(R.id.routeListTime);
    timeView.setText(getRouteTime(route));
  }

  public static String getRouteDistance(final Route route) {
    return String.format("%1.1fmi", route.getDistance() / 1600.0);
  }

  public static String getRouteTimeAgo(final Route route) {
    long routeStart = route.getStartTime();
    long now = System.currentTimeMillis();
    long ago = now - routeStart;
    String dateString;
    if (ago < MILLIS_PER_HOUR) {
      dateString = String.format("%d minutes ago", ago / MILLIS_PER_MINUTE);
    } else if (ago < 3 * MILLIS_PER_HOUR) {
      dateString = String.format("%d hours ago", ago / MILLIS_PER_HOUR);
    } else {
      dateString = dateFormat.format(DATE_FORMAT, route.getEndTime()).toString();
    }
    return dateString;
  }

  public static String getRouteTime(final Route route) {
    long routeTime = route.getEndTime() - route.getStartTime();;
    String time = String.format("Time: %d minutes", routeTime / MILLIS_PER_MINUTE);
    return time;
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    LayoutInflater inflater = ((Activity) context).getLayoutInflater();
    return inflater.inflate(R.layout.route_list_item, parent, false);
  }
}
