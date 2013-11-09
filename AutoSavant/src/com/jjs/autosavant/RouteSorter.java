package com.jjs.autosavant;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jjs.autosavant.proto.Place;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

public class RouteSorter {
  public enum SortBy {
    DATE_DESC("Date (Newest)"),   
    DATE_ASC("Date (Oldest)"),
    FROM("Starting Place"),
    TO("Destination");
    
    private String label;
    
    SortBy(String label) {
     this.label = label; 
    }
    
    public String toString() {
      return label;
    }
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
      return createList(context, routes, sortBy);
    } else if (sortBy == SortBy.FROM || sortBy == SortBy.TO) {
      boolean isFrom = sortBy == SortBy.FROM;
      List<RouteContainer> routesWithPlaces = Lists.newArrayList();
      Set<String> places = Sets.newHashSet();
      for (Route route : routes) {
        Place place = placeStorage.getPlaceForRoute(
            isFrom ? route.getRoutePoint(0) : RouteUtils.getLastRoutePoint(route));
        String label = place == null ? "Unknown" : place.getName();
        routesWithPlaces.add(new RouteContainer(route, label));
        if (!places.contains(label)) {
          places.add(label);
          routesWithPlaces.add(new RouteContainer(null, label));
        }
      }
      Collections.sort(routesWithPlaces, new Comparator<RouteContainer>() {
        @Override
        public int compare(RouteContainer r0, RouteContainer r1) {
          int compare = r0.getDivider().compareTo(r1.getDivider());
          if (compare == 0) {
            if (r0.getRoute() == null) { 
              return -1;
            } else if (r1.getRoute() == null) {
              return 1;
            } else {
              return (int) (r1.getRoute().getStartTime() - r0.getRoute().getStartTime());
            }
          }
          return compare;
        }
      });
      return routesWithPlaces;
    } else {
      System.err.println("No such SortBy: " + sortBy);
      return null;
    }
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
