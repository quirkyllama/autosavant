package com.jjs.autosavant;

import java.util.List;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class ConfigActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_config);
    RadioGroup deviceGroup = (RadioGroup) findViewById(R.id.chooseDevice);
    final List<BluetoothDevice> devices = BluetoothListener.getDevices();
    if (devices.isEmpty()) {
      showNoDevices();
    } else {
      LayoutInflater inflater = getLayoutInflater();
      for (int i = 0; i < devices.size(); i++) {
        final BluetoothDevice device = devices.get(i);
        RadioButton button = (RadioButton) inflater.inflate(R.layout.choose_device_button, null);
        button.setText(device.getName());
        button.setId(i);
        button.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            if (isChecked) {
              selectDevice(device);
            }
          }  
        });
        deviceGroup.addView(button);
      }
    }
  }

  private void selectDevice(BluetoothDevice device) {
    BluetoothListener.selectDevice(device);
  }

  private void showNoDevices() {
    TextView noDevicesError = (TextView) findViewById(R.id.noDevicesError);
    noDevicesError.setVisibility(View.VISIBLE);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.activity_config, menu);
    
    return true;
  }
}
