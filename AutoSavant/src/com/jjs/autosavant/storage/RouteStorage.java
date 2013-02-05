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
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.jjs.autosavant.proto.Route;

public class RouteStorage {
  private static final String ROUTE_TABLE = "Routes";
  private static final String DB_NAME = "AutoSavantDB";
  private static final String DATE_COLUMN = "date_";
  private static final String PROTO_COLUMN = "data_";
  private static final String TAG = "Storage";

  private final Context context;
  private final SQLiteDatabase database;

  public RouteStorage(Context context) {
    super();
    this.context = context;

    database = 
        new CreateDatabase(context).getWritableDatabase(); 
  }


  public Route parseFromCursor(Cursor cursor) {
    byte[] data = cursor.getBlob(1);
    try {
      return Route.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      Log.v(TAG, e.getMessage());
    }
    return null;
  }

  public List<Route> getRoutes() {
    Cursor cursor = getRouteCursor();
    List<Route> routes = new ArrayList<Route>();
    while (!cursor.isAfterLast()) {
      cursor.moveToNext();
      byte[] data = cursor.getBlob(1);
      try {
        Route route = Route.parseFrom(data);
        routes.add(route);
      } catch (InvalidProtocolBufferException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        Log.v(TAG, e.getMessage());
      }
    }
    cursor.close();
    return routes;
  }

  public Cursor getRouteCursor() {
    String query = SQLiteQueryBuilder.buildQueryString(
        false,  ROUTE_TABLE, new String[]{DATE_COLUMN + " as _id", PROTO_COLUMN}, null, null, null, DATE_COLUMN + " desc", null);
    Log.v(TAG, "Reading query: " + query);
    Cursor cursor = database.rawQuery(query, null);
    return cursor;
  }

  public void saveRoute(Route route) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try {
      route.writeTo(os);
      long time = route.getEndTime();
      ContentValues content = new ContentValues();
      content.put(DATE_COLUMN, time);
      content.put(PROTO_COLUMN, os.toByteArray());
      database.insert(ROUTE_TABLE, null, content);
    } catch (IOException e) {
      e.printStackTrace();
      Log.v(TAG, "Error saving route: " + e.getMessage());
    }
  }

  static class CreateDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_CREATE = 
        "create table " + ROUTE_TABLE + "("
        + DATE_COLUMN + " integer primary key, "
        + PROTO_COLUMN + " blob not null);";


    public CreateDatabase(Context context) {
      super(context, DB_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      System.err.println("Creating AutoSavant DB");
      db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {

    }
  }
}
