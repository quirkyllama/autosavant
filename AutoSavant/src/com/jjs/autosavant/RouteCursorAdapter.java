package com.jjs.autosavant;

import com.jjs.autosavant.R;
import com.jjs.autosavant.RouteSorter.RouteContainer;
import com.jjs.autosavant.RouteSorter.SortBy;
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
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RouteCursorAdapter extends ArrayAdapter<RouteContainer> {
  private static final long MILLIS_PER_MINUTE = 60 * 1000;
  private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
  private static java.text.DateFormat dateFormat;
//  private final RouteStorage storage;
  private final PlaceStorage placeStorage;
  private final RouteClickListener listener;
  private final Activity activity;
  
  public interface RouteClickListener {
    public void onClick(Route route);
  }
  
  public RouteCursorAdapter(Activity activity, RouteStorage storage, PlaceStorage placeStorage,
      SortBy sortBy, RouteClickListener listener) {
    super(activity, R.layout.route_list_item, 0, 
        RouteSorter.getSorter(activity, storage, placeStorage, sortBy));
    this.listener = listener;
    this.activity = activity;
    this.placeStorage = placeStorage;
  }

  @Override
  public View getView(int index, View view, ViewGroup viewGroup) {
    RouteContainer routeContainer = getItem(index);
    if (routeContainer.getRoute() != null) {
      if (view == null || view instanceof TextView) {
        view = activity.getLayoutInflater().inflate(R.layout.route_list_item, null);
      }
      setupListView(view, routeContainer.getRoute(), placeStorage, listener);
    } else {
      if (view == null || !(view instanceof TextView)) {
        view = activity.getLayoutInflater().inflate(R.layout.route_list_divider, null);
      }
      ((TextView) view).setText(routeContainer.getDivider());
    }
    
    return view;
  }

  public static void setupListView(View view, final Route route, PlaceStorage placeStorage,
      final RouteClickListener listener) {
    Place to = placeStorage.getPlaceForRoute(RouteUtils.getLastRoutePoint(route));
    Place from = placeStorage.getPlaceForRoute(route.getRoutePoint(0));
    String text = String.format("%s --> %s",
        from != null ? from.getName() : "unknown",
        to != null ? to.getName() : "unknown");
            
    setText(view, R.id.routeLabel, text);
    //setText(view, R.id.routeDate, getRouteTimeAgo(view.getContext(), route));
    setText(view, R.id.routeListDistance, getRouteDistance(route));
    setText(view, R.id.routeListTime, getRouteTime(route));
    View iconView = view.findViewById(R.id.routeListDetails);
    if (listener != null) {
      iconView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          System.err.println("Click on route");
          listener.onClick(route);
        }
      });
    } else {
      iconView.setVisibility(View.GONE);
    }
  }

  private static void setText(View view, int id, String label) {
    TextView textView = (TextView) view.findViewById(id); 
    textView.setText(label);
  }

  public static String getRouteDistance(final Route route) {
    return String.format("%1.1fmi", route.getDistance() / 1600.0);
  }

  public static String getRouteTimeAgo(final Context context, final Route route) {
    long routeStart = route.getStartTime();
    long now = System.currentTimeMillis();
    long ago = now - routeStart;
    String dateString;
    if (ago < MILLIS_PER_HOUR) {
      dateString = String.format("%d minutes ago", ago / MILLIS_PER_MINUTE);
    } else if (ago < 3 * MILLIS_PER_HOUR) {
      dateString = String.format("%d hours ago", ago / MILLIS_PER_HOUR);
    } else {
      dateString = getDateFormat(context).format(route.getEndTime());
    }
    return dateString;
  }

  public static java.text.DateFormat getDateFormat(Context context) {
    if (dateFormat == null) {
      dateFormat = DateFormat.getDateFormat(context);
    }
    return dateFormat;
  }

  public static String getRouteTime(final Route route) {
    long routeTime = route.getEndTime() - route.getStartTime();;
    String time = String.format("%d minutes", routeTime / MILLIS_PER_MINUTE);
    return time;
  }
}
