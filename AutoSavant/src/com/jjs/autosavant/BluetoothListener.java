package com.jjs.autosavant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.LocationManager;
import android.util.Log;

public class BluetoothListener {
  private static final String PREF = "BL_PREFS";
  private static final String DEVICE_ADDRESS = "DeviceAddress";
  private static final String TAG = "BT_EVENT";
  public static final String STATE_EXTRA = "StateExtra";
  public static final String CONNECT = "Connect";
  public static final String DISCONNECT = "Disconnect";

  
  private final Context context;
  private String selectedDeviceAddress;
  private String selectedDevice;
  
  public BluetoothListener(Context context) {
    Log.v(TAG, "New BL listener");
    this.context = context;
    SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    selectedDeviceAddress = verifyAddress(prefs.getString(DEVICE_ADDRESS, null));
  }

  // Verifies that address is non-null and maps to a valid Bluetooth device.
  private String verifyAddress(String address) {
    Log.v(TAG, "VerifingAddress: " + address);
    if (address == null) {
      Log.v(TAG, "No previous device!");
      return address;
    }
    
    List<BluetoothDevice> devices = getDevices();
    for (BluetoothDevice device : devices) {
      if (device.getAddress().equals(address)) {
        Log.v(TAG, "Using device: "+ device.getName());
        selectedDevice = device.getName();
        return address;
      }
    }
    Log.v(TAG, "Addresss not verified");
    return null;
  }

  public static final class Receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
        Log.v(TAG, "Ignoring BT event: " + intent.getAction());
        return;
      }
      Log.v(TAG, "BT event has extra: "  + 
          intent.hasExtra(BluetoothProfile.EXTRA_STATE));
      int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
              BluetoothProfile.STATE_DISCONNECTED);
      boolean isStart = state == BluetoothProfile.STATE_CONNECTED;
      BluetoothListener listener = new BluetoothListener(context);
      Log.v(TAG, "BT event status: " + isStart + " " + state);

      BluetoothDevice intentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

      if (intentDevice == null) {
        Log.e(TAG, "No IntentDevice!");
        return;
      }
      if (!intentDevice.getAddress().equals(listener.selectedDeviceAddress)) {
        Log.v(TAG,  "Ignored BT for device: " + intentDevice.getName());
        return;
      } else {
        Log.v(TAG, "Starting InCarService");
        Intent startIntent = new Intent(context, InCarService.class);
        startIntent.putExtra(STATE_EXTRA, isStart ? CONNECT : DISCONNECT);
        context.startService(startIntent);
      }
    }
  }
  
  public static List<BluetoothDevice> getDevices() {
    List<BluetoothDevice> matchingDevices = new ArrayList<BluetoothDevice>();
       
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      Log.v(TAG, "No BT adapter");
      return matchingDevices;
    }
    Set<BluetoothDevice> devices = adapter.getBondedDevices();
    for (BluetoothDevice device : devices) {
      BluetoothClass deviceClass = device.getBluetoothClass();
      if (deviceClass.hasService(BluetoothClass.Service.AUDIO) ||
          deviceClass.hasService(BluetoothClass.Service.TELEPHONY)) {
        //Log.v(TAG, device.getName() + " Has Audio");
        matchingDevices.add(device);
      }
    }
    return matchingDevices;
  } 

  public void selectDevice(BluetoothDevice device) {
    Log.v(TAG,  "Selecting device: " + device.getName());
    selectedDeviceAddress = device.getAddress();
    selectedDevice = device.getName();
    SharedPreferences prefs = 
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    Editor editor = prefs.edit();
    editor.putString(DEVICE_ADDRESS, device.getAddress());
    editor.apply();
  }

  public boolean isConfigured() {
    return selectedDeviceAddress != null;
  }

  public String getSelectedDevice() {
    return selectedDevice;
  }
}
