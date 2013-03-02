package com.jjs.autosavant;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import com.google.common.collect.Lists;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

public class RouteSorter {
  public enum SortBy {
    DATE_ASC,
    DATE_DESC,
  }
    
  public static List<RouteContainer> getSorter(
      Context context, RouteStorage routeStorage, PlaceStorage placeStorage, SortBy sortBy) {
    List<Route> routes = routeStorage.getRoutes();
    if (sortBy == SortBy.DATE_ASC || sortBy == SortBy.DATE_DESC) {
      final boolean ascending = sortBy == SortBy.DATE_ASC;
      Collections.sort(routes, new Comparator<Route>() {
        @Override
        public int compare(Route r1, Route r2) {
          int delta = (int) (r1.getStartTime() - r2.getStartTime());
          return ascending ? delta : -delta;
        }
      });
    }
    return createList(context, routes, sortBy);
  }
  private static String getDate(Route route, DateFormat dateFormat) {
    long time = route.getStartTime();
    long now = System.currentTimeMillis();
    if (now - time < TimeUnit.DAYS.toMillis(1)) {
      return "Today";
    } else if (now - time < TimeUnit.DAYS.toMillis(2)) {
      return "Yesterday";
    } else {
      return dateFormat.format(time);
    }
  }
  
  private static List<RouteContainer> createList(
      Context context, List<Route> routes, SortBy sortBy) {
    int lastDay = -1;
    Locale locale = context.getResources().getConfiguration().locale;
    Calendar calendar = GregorianCalendar.getInstance(locale);
    List<RouteContainer> routeContainers = Lists.newArrayList();
    DateFormat dateFormat = RouteCursorAdapter.getDateFormat(context);
    for (Route route : routes) {
      calendar.setTimeInMillis(route.getStartTime());
      int currentDay = calendar.get(Calendar.DAY_OF_YEAR) + calendar.get(Calendar.YEAR) * 366;
      if (currentDay != lastDay) {
        lastDay = currentDay;
        routeContainers.add(new RouteContainer(null, getDate(route, dateFormat)));
      }
      routeContainers.add(new RouteContainer(route, null));
    }
    return routeContainers;
  }

  public static class RouteContainer {
    private final Route route;
    private final String divider;
        
    private RouteContainer(Route route, String divider) {
      super();
      this.route = route;
      this.divider = divider;
    }

    public Route getRoute() {
      return route;
    }
    public String getDivider() {
      return divider;
    }   
  }
}
