package com.jjs.autosavant.proto;

import com.jjs.autosavant.R;
import com.jjs.autosavant.storage.RouteStorage;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class RouteCursorAdapter extends CursorAdapter {
  private static final long MILLIS_PER_MINUTE = 60 * 1000;
  private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
  
  private final RouteStorage storage;
  private final RouteClickListener listener;
  
  public interface RouteClickListener {
    public void onClick(Route route);
  }
  
  public RouteCursorAdapter(Context context, RouteStorage storage, RouteClickListener listener) {
    super(context, storage.getRouteCursor(), FLAG_REGISTER_CONTENT_OBSERVER);
    this.storage = storage;
    this.listener = listener;
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    TextView dateView = (TextView) view.findViewById(R.id.routeListDate);
    final Route route = storage.parseFromCursor(cursor);
    view.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        System.err.println("Click on route");
        listener.onClick(route);
      }
    });
    
    long routeStart = route.getStartTime();
    long now = System.currentTimeMillis();
    long ago = now - routeStart;
    String dateString;
    if (ago < MILLIS_PER_HOUR) {
      dateString = String.format("%d minutes ago", ago / MILLIS_PER_MINUTE);
    } else if (ago < 20 * MILLIS_PER_HOUR) {
      dateString = String.format("%d hours ago", ago / MILLIS_PER_HOUR);
    } else {
      dateString = String.format("%d days ago", 1 + ago / ( 24 * MILLIS_PER_HOUR));
    }
    
    dateView.setText(dateString);
    TextView distanceView = (TextView) view.findViewById(R.id.routeListDistance);
    String distance = String.format("Distance: %1.1f", route.getDistance() / 1600.0);
    distanceView.setText(distance);

    TextView timeView = (TextView) view.findViewById(R.id.routeListTime);
    long routeTime = route.getEndTime() - routeStart;
    String time = String.format("Time: %d minutes", routeTime / MILLIS_PER_MINUTE);
    timeView.setText(time);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    LayoutInflater inflater = ((Activity) context).getLayoutInflater();
    return inflater.inflate(R.layout.route_list_item, parent, false);
  }
}
