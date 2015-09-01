package com.tmccapital.hfm_2;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import com.tmccapital.hfm_2.Constants;

public class DispenseFuel extends AppCompatActivity {

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    private Button scan_btn;
    private String mConnectedDeviceName = null;
    private String mChosenAddress;
    private ArrayAdapter<String> mConversationArrayAdapter;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothService mBluetoothService;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dispense_fuel);

        //Let's instantiate our button
        scan_btn = (Button) findViewById(R.id.dispense_scan_btn);
        scan_btn.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view){
                Intent intent = new Intent(DispenseFuel.this, ListActivity.class);
                startActivityForResult(intent, 1);
                String address = intent.getExtras().getString(ListActivity.EXTRA_DEVICE_ADDRESS);
                mChosenAddress = address;
            }
        });

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            this.finish();
        }
        if (mBluetoothService == null){
            setupBT();
        }
        Button go = (Button)findViewById(R.id.dispense_disp_btn);
        go.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                //Initialise all our text boxes
                EditText eqID = (EditText) view.findViewById(R.id.dispense_eq_id_box);
                EditText make = (EditText) view.findViewById(R.id.dispense_make_box);
                EditText model = (EditText) view.findViewById(R.id.dispense_model_box);
                EditText odo = (EditText) view.findViewById(R.id.dispense_odo_box);
                EditText uom = (EditText) view.findViewById(R.id.dispense_uom_box);
                EditText notes = (EditText) view.findViewById(R.id.dispense_notes_box);

                String transInfo = "Device ID: #####\n"; //Get the MAC or other identifier of the Arduino
                transInfo = transInfo + "Local Transaction ID: #####\n"; //Given by Arduino
                transInfo = transInfo + "Remote Transaction ID: ###\n"; //Given by DB
                transInfo = transInfo + "Equipment ID: " + eqID.getText().toString() + "\n";
                transInfo = transInfo + "Make: " + make.getText().toString() + " Model: " + model.getText().toString() + "\n";
                transInfo = transInfo + "Odo: " + odo.getText().toString() + " " + uom.getText().toString() + "\n";
                transInfo = transInfo + "Notes: \n" + notes.getText().toString();
                transInfo = transInfo + "Attempting Bluetooth Connection\n";

                try {
                    FileOutputStream outStream = openFileOutput("log", Context.MODE_PRIVATE);
                    outStream.write("New Log\n".getBytes());
                    outStream.write(getCurrentTimeStamp().getBytes());
                    outStream.write(transInfo.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }



                //Let's attempt a connection
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mChosenAddress);
                mBluetoothService.connect(device, false);

                //Begin Data Transfer


                Intent intent = new Intent(DispenseFuel.this, DispenseSpinner.class);
                DispenseFuel.this.startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_dispense_fuel, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = DispenseFuel.this;
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:

                            break;
                        case BluetoothService.STATE_CONNECTING:

                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:

                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("SEND:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(activity, "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


    private void setupBT(){
        Log.d(Constants.TAG,"setupBT()");

        mConversationArrayAdapter = new ArrayAdapter<String>(DispenseFuel.this, R.layout.message);

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBluetoothService = new BluetoothService(DispenseFuel.this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
}

