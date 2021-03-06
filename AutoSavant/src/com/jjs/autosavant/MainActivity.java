package com.jjs.autosavant;

import java.util.List;

import com.jjs.autosavant.RouteCursorAdapter.RouteClickListener;
import com.jjs.autosavant.proto.Route;
import com.jjs.autosavant.storage.PlaceStorage;
import com.jjs.autosavant.storage.RouteStorage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

public class MainActivity extends Activity {
  private RouteStorage routeStorage;
  private PlaceStorage placeStorage;
  private BluetoothListener bluetoothListener;
  private RouteSorter.SortBy sortOrder;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    sortOrder = RouteSorter.SortBy.DATE_DESC;
    routeStorage = new RouteStorage(this);
    routeStorage.cleanRoutes();
    placeStorage = new PlaceStorage(this);
    showListView();
    bluetoothListener = new BluetoothListener(this);
    if (!bluetoothListener.isConfigured()) {
      configureDevice();
    }
  }

  private void showListView() {
    setContentView(R.layout.activity_config);
    final ListView listView = (ListView) findViewById(R.id.routeList);
    final View header = getLayoutInflater().inflate(R.layout.route_list_sort_by, null);
    final Spinner spinner = (Spinner) header.findViewById(R.id.sortBySpinner);
    ArrayAdapter<RouteSorter.SortBy> sortByChoices = new ArrayAdapter<RouteSorter.SortBy>(
            this,  R.layout.route_list_sort_by_item, RouteSorter.SortBy.values());
    sortByChoices.setDropDownViewResource(R.layout.route_list_sort_by_item);
    spinner.setAdapter(sortByChoices);
    spinner.setSelection(sortOrder.ordinal());
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapter, View view, int index, long id) {
        sortOrder = RouteSorter.SortBy.values()[index];
        setListViewAdapter(listView);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });
    listView.addHeaderView(header);
    setListViewAdapter(listView);
  }

  public void setListViewAdapter(final ListView listView) {
    listView.setAdapter(
        new RouteCursorAdapter(this, routeStorage, placeStorage, sortOrder,
            new RouteClickListener() {
      @Override
      public void onClick(Route route) {
        Intent intent = new Intent(MainActivity.this, ShowRouteMapActivity.class);
        intent.putExtra(ShowRouteMapActivity.SHOW_ROUTE_EXTRA_PROTO, route.toByteArray());
        startActivity(intent);
      }}
    ));
  }

  private void configureDevice() {
     List<BluetoothDevice> devices = BluetoothListener.getDevices();
     ChooseDeviceDialogFragment dialogFragment =
             new ChooseDeviceDialogFragment(devices);
     dialogFragment.show(getFragmentManager(),"ChooseDeviceFragment");
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.activity_config, menu);
    MenuItem item = menu.findItem(R.id.bluetooth_pick);
    item.setOnMenuItemClickListener(
        new OnMenuItemClickListener() {                 
          @Override
          public boolean onMenuItemClick(MenuItem item) {
            System.err.println("Got click");
            configureDevice();
            return true;
          }
        });
    return true;
  }

  protected void selectDevice(BluetoothDevice bluetoothDevice) {
    bluetoothListener.selectDevice(bluetoothDevice);
  }

  public class ChooseDeviceDialogFragment extends DialogFragment {
    private final List<BluetoothDevice> devices;
    
    public ChooseDeviceDialogFragment(List<BluetoothDevice> devices) {
      super();
      this.devices = devices;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      // Use the Builder class for convenient dialog construction
      AlertDialog.Builder builder = 
              new AlertDialog.Builder(getActivity());
      builder.setTitle("Pick Bluetooth device:");

      if (devices.size() == 0) {
        builder.setMessage(
                "No suitable bluetooth devices found. " +
                "AutoSavant uses your car's bluetooth system to automatically " +
                "detect when you enter and exit your vehicle. " +
                "You must have a handsfree system in your car for this " + 
                "application to work. You do NOT need to be connected to " +
                "the system in order to setup AutoSavant.");
      }
      final String[] deviceNames = new String[devices.size()];
      for (int i = 0; i < deviceNames.length; i++) {
        deviceNames[i] = devices.get(i).getName();
      }
      builder.setItems(deviceNames, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          selectDevice(devices.get(which));          
          dialog.dismiss();
        }
      });

      return builder.create();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    placeStorage.update();
    showListView();
  }
}
