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
import android.util.Log;

public class BluetoothListener {
  private static final String PREF = "BL_PREFS";
  private static final String DEVICE_ADDRESS = "DeviceAddress";
  private static final String TAG = "BT_EVENT";
  
  private final Context context;
  private String selectedDeviceAddress;
  
  public BluetoothListener(Context context) {
    this.context = context;
    SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    selectedDeviceAddress = verifyAddress(prefs.getString(DEVICE_ADDRESS, null));
  }

  // Verifies that address is non-null and maps to a valid Bluetooth device.
  private String verifyAddress(String address) {
    if (address == null) {
      return address;
    }
    
    List<BluetoothDevice> devices = getDevices();
    for (BluetoothDevice device : devices) {
      if (device.getAddress().equals(address)) {
        return address;
      }
    }
    return null;
  }

  public static final class Receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
        Log.v(TAG, "Ignoring BT event: " + intent.getAction());
        return;
      }
      Log.v(TAG, "BT event");
      int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
              BluetoothProfile.STATE_DISCONNECTED);
      BluetoothListener listener = new BluetoothListener(context);
      Log.v(TAG, "BT event status: " + state);
      if (state != BluetoothProfile.STATE_CONNECTED) {
        state = BluetoothProfile.STATE_DISCONNECTED;
      }

      BluetoothDevice intentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

      if (intentDevice == null) {
        Log.e(TAG, "No IntentDevice!");
        return;
      }
      if (!intentDevice.getAddress().equals(listener.selectedDeviceAddress)) {
        Log.v(TAG,  "Ignored BT for device: " + intentDevice.getName());
        return;
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
        Log.v(TAG, device.getName() + " Has Audio");
        matchingDevices.add(device);
      }
    }
    return matchingDevices;
  } 

  public void selectDevice(Context context, BluetoothDevice device) {
    
    // TODO Auto-generated method stub
    
  }
}
