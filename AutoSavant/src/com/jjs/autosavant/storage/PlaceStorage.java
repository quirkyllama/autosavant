package com.jjs.autosavant.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.location.Location;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.jjs.autosavant.proto.Place;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.proto.RoutePoint;

public class PlaceStorage {
  private static final String PLACE_TABLE = "Places";

  private static final String DATE_COLUMN = "date_";
  private static final String PROTO_COLUMN = "data_";
  private static final String TAG = "Storage";
  private static final String DB_NAME = "PlaceDb2";

  private static final double MAX_PLACE_DISTANCE = 150;

  private final Context context;
  private final SQLiteDatabase database;
  private List<Place> places;
  
  public PlaceStorage(Context context) {
    super();
    this.context = context;
    database = 
        new CreateDatabase(context).getWritableDatabase(); 
  }

  public Place getPlaceForRoute(RoutePoint point) {
    Location parkingSpot = new Location("");
    parkingSpot.setLatitude(point.getLatitude());
    parkingSpot.setLongitude(point.getLongitude());
    
    List<Place> places = getPlaces();
    Place bestPlace = null;
    double bestDistance = Double.MAX_VALUE;
    for (Place place : places) {
      Location end = new Location("");
      end.setLatitude(place.getLatitude());
      end.setLongitude(place.getLongitude());
      double distance = end.distanceTo(parkingSpot);
      if (distance < bestDistance) {
        bestDistance = distance;
        bestPlace = place;
      }
    }
    
    if (bestDistance < MAX_PLACE_DISTANCE) {
      return bestPlace;
    }
    return null;
  }
  
  public List<Place> getPlaces() {
    if (places != null) {
      return places;
    }
    Cursor cursor = getPlaceCursor();
    List<Place> places = new ArrayList<Place>();
    while (cursor.moveToNext()) {
      System.err.println("Reading: " +  places.size());
      byte[] data = cursor.getBlob(1);
      try {
        Place place = Place.parseFrom(data);
        places.add(place);
      } catch (InvalidProtocolBufferException e) {
        e.printStackTrace();
        Log.v(TAG, e.getMessage());
      }
    }
    cursor.close();
    return places;
  }

  private Cursor getPlaceCursor() {
    String query = SQLiteQueryBuilder.buildQueryString(
        false,  PLACE_TABLE, new String[]{DATE_COLUMN + " as _id", PROTO_COLUMN}, null, null, null, DATE_COLUMN + " desc", null);
    Log.v(TAG, "Reading query places: " + query);
    return database.rawQuery(query, null);
  }

  public void savePlace(Place place, boolean update) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try {
      place.writeTo(os);
      long time = place.getTime();
      ContentValues content = new ContentValues();
      content.put(DATE_COLUMN, time);
      content.put(PROTO_COLUMN, os.toByteArray());
      if (update) {
       database.update(PLACE_TABLE, content, "DATE_COLUMN = ?", new String[] {"" + time}); 
      } else {
        database.insert(PLACE_TABLE, null, content);
      }
    } catch (IOException e) {
      e.printStackTrace();
      Log.v(TAG, "Error saving route: " + e.getMessage());
    }
    places = null;
  }

  static class CreateDatabase extends SQLiteOpenHelper {
    private static final String PLACE_CREATE_DB = 
        "create table " + PLACE_TABLE + "("
        + DATE_COLUMN + " integer primary key, "
        + PROTO_COLUMN + " blob not null);";

    public CreateDatabase(Context context) {
      super(context, DB_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      System.err.println("Creating Place DB");
      db.execSQL(PLACE_CREATE_DB);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {

    }
  }
}
