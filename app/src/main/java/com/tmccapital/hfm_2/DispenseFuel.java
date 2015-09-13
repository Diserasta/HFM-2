package com.tmccapital.hfm_2;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StrictMode;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import org.kawanfw.sql.api.client.RemoteDriver;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DispenseFuel extends AppCompatActivity implements RadioGroup.OnCheckedChangeListener {

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private Spinner eqID;
    private Button scan_btn;

    private String mConnectedDeviceName = null;
    private String mChosenAddress;
    private ArrayAdapter<String> mConversationArrayAdapter;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothService mBluetoothService;
    UartService mUartService;
    Connection mSqlConnect;
    List<String> spinnerArray;
    Location currLoc;

    //Initialise all our text boxes
    EditText make;
    EditText model;
    EditText odo;
    EditText uom;
    EditText notes;

    ArrayList<EquipmentItem> eqList = new ArrayList<>();
    EquipmentItem curr;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    // Acquire a reference to the system Location Manager
    LocationManager locationManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dispense_fuel);

        //Let's fuck with best practises
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        //Let's instantiate our button
        scan_btn = (Button) findViewById(R.id.dispense_scan_btn);
        scan_btn.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view){
                Intent intent = new Intent(DispenseFuel.this, ListActivity.class);
                startActivityForResult(intent, 1);
                //String address = intent.getExtras().getString(BluetoothDevice.EXTRA_DEVICE);
                //mChosenAddress = address;
            }
        });

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            this.finish();
        }
        if (mBluetoothService == null){
            setupBT();
        }

        //Initialise the UartService
        service_init();

        //Initialise the AceQL Library
        //Make this a setting at some point
        String url = "jdbc:aceql:http://45.32.243.135:9090/ServerSqlManager";
        String username = "root";
        String pwd = "-+Y6b9+%.Y^H"; //*internally screams about security*
        RemoteDriver aceql = new RemoteDriver();


        try {
            Class.forName("org.kawanfw.sql.api.client.RemoteDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        /**
        try{
            DriverManager.registerDriver(aceql);
        } catch (SQLException e){
            e.printStackTrace();
        }
        **/
        //Let's try and get the remote server
        try {
            mSqlConnect = DriverManager.getConnection(url,username,pwd);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (mSqlConnect != null){
            Log.d(Constants.TAG, "Connection Established!");
        }

        //Retrieve the equipmentlist
        try {
            selectEquipment();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //Instantiate our boxes
        //Initialise all our text boxes
        make = (EditText) findViewById(R.id.dispense_make_box);
        model = (EditText) findViewById(R.id.dispense_model_box);
        odo = (EditText) findViewById(R.id.dispense_odo_box);
        uom = (EditText) findViewById(R.id.dispense_uom_box);
        notes = (EditText) findViewById(R.id.dispense_notes_box);

        //Populate our Spinner
        spinnerArray =  new ArrayList<String>();
        for (EquipmentItem eq : eqList){
            spinnerArray.add(eq.name);
        }

        final ArrayAdapter<EquipmentItem> adapter = new ArrayAdapter<EquipmentItem>(
                this, android.R.layout.simple_spinner_item, eqList);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eqID = (Spinner) findViewById(R.id.dispense_eq_id_spinner);

        eqID.setAdapter(adapter);

        eqID.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                curr = (EquipmentItem) eqID.getSelectedItem();
                make.setText(curr.make);
                model.setText(curr.model);
                uom.setText(curr.uom);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Next

        //Let's start the LocationManager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        //What we do when we hit that go button
        Button go = (Button)findViewById(R.id.dispense_disp_btn);
        go.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                double gps_lat = currLoc.getLatitude();
                double gps_long = currLoc.getLongitude();

                String transInfo = "Device ID: #####\n"; //Get the MAC or other identifier of the Arduino
                //transInfo = transInfo + "Local Transaction ID: #####\n"; //Given by Arduino
                //transInfo = transInfo + "Remote Transaction ID: ###\n"; //Given by DB
                //transInfo = transInfo + "Equipment ID: " + eqID.getSelectedItem().toString() + "\n";
                //transInfo = transInfo + "Make: " + make.getText().toString() + " Model: " + model.getText().toString() + "\n";
                //transInfo = transInfo + "Odo: " + odo.getText().toString() + " " + uom.getText().toString() + "\n";
                //transInfo = transInfo + "Notes: \n" + notes.getText().toString();
                //transInfo = transInfo + "Attempting Bluetooth Connection\n";

                try {
                    FileOutputStream outStream = openFileOutput("log", Context.MODE_PRIVATE);
                    outStream.write("New Log\n".getBytes());
                    outStream.write(getCurrentTimeStamp().getBytes());
                    outStream.write(transInfo.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }



                //Let's attempt a connection
                //BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mChosenAddress);
                //mBluetoothService.connect(device, false);
                mUartService.connect(mChosenAddress);

                //Begin Data Transfer
                String tmp = "A45" + "000001" + "0000123456\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "B310000123400005678\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "C96" + String.valueOf(gps_lat);
                for (int i = 19 - tmp.length(); i > 0; i--){
                    tmp += '0';
                }
                tmp += '\n';
                Log.d(Constants.TAG, "GPS Lat is: " + gps_lat + " tmp is: " + tmp);
                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "D85" + String.valueOf(gps_long);
                for (int i = 19 - tmp.length(); i > 0; i--){
                    tmp += '0';
                }
                tmp += '\n';
                Log.d(Constants.TAG, "GPS Long is: " + gps_long + " tmp is: " + tmp);
                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "E62" + curr.equipment_id;
                for (int i = 8 - eqID.getSelectedItem().toString().length(); i > 0; i--){
                    tmp += '0';
                }
                tmp += odo.getText().toString();
                for (int i = 8 - eqID.getSelectedItem().toString().length(); i > 0; i--){
                    tmp += '0';
                }
                tmp += '\n';

                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "F130L0K0A0L00000000\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                tmp = "H22abcdefghklmnopqr\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());



                //Go cmd goes here
                tmp = "K07-CMD-GOAUTH00\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                //curr = (EquipmentItem) eqID.getSelectedItem();
                Log.d(Constants.TAG, "curr EQID is: " + curr.equipment_id);
                //Let's attempt our transaction
                //TODO: DATA VALIDATION ON THE DAMN ODO

                try {
                    sendOrder(4,1,2, (int) curr.equipment_id,
                            Integer.parseInt( odo.getText().toString() ),
                            gps_lat, gps_long, 400, 20.00,
                            notes.getText().toString());
                } catch (SQLException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),"Failed  to push data to SQL Server",
                            Toast.LENGTH_SHORT).show();
                }


                Intent intent = new Intent(DispenseFuel.this, DispenseSpinner.class);
                DispenseFuel.this.startActivity(intent);
                tmp = "K07-STOP000000"; //Clear code is K07-CLEAR-ALL0
                mUartService.writeRXCharacteristic(tmp.getBytes());
            }
        });
    }


    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mUartService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(Constants.TAG, "onServiceConnected mService= " + mUartService);
            if (!mUartService.initialize()) {
                Log.e(Constants.TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mUartService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(Constants.TAG, "UART_CONNECT_MSG");
                        //btnConnectDisconnect.setText("Disconnect");
                        //edtMessage.setEnabled(true);
                        //btnSend.setEnabled(true);
                        //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        //listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        //messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                        //mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        //String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(Constants.TAG, "UART_DISCONNECT_MSG");
                        ///btnConnectDisconnect.setText("Connect");
                        //edtMessage.setEnabled(false);
                        //btnSend.setEnabled(false);
                        //((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        //listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                        //mState = UART_PROFILE_DISCONNECTED;
                        mUartService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mUartService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            //String text = new String(txValue, "UTF-8");
                            //String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            //listAdapter.add("["+currentDateTimeString+"] RX: "+text);
                            //messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

                        } catch (Exception e) {
                            Log.e(Constants.TAG, e.toString());
                        }
                    }
                });
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mUartService.disconnect();
            }


        }
    };

    // Define a listener that responds to location updates
    public LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            currLoc = location;
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };


    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(Constants.TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(Constants.TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mUartService.stopSelf();
        mUartService= null;

    }

    @Override
    protected void onStop() {
        Log.d(Constants.TAG, "onStop");
        locationManager.removeUpdates(locationListener);
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(Constants.TAG, "onPause");
        locationManager.removeUpdates(locationListener);
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(Constants.TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(Constants.TAG, "onResume");
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(Constants.TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mChosenAddress = deviceAddress;

                    Log.d(Constants.TAG, "... onActivityResultdevice.address==" + mChosenAddress + "mserviceValue" + mUartService);
                    //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mUartService.connect(deviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(Constants.TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(Constants.TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

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


    private void setupBT(){
        Log.d(Constants.TAG,"setupBT()");

        mConversationArrayAdapter = new ArrayAdapter<String>(DispenseFuel.this, R.layout.message);

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBluetoothService = new BluetoothService(DispenseFuel.this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }



    /**

     * An example of INSERT

     */

    public void sendOrder(int customerID,int userID,int deviceID, int eqID, int odo,
                          double lat, double longt, int pulse, double vol,
                          String notes) throws SQLException{
        String sql = "insert into transactions (client_id, user_id, device_id, equipment_id, project_id, odo, notes, gps_lat, gps_long, date_time, pulse, vol)" +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement prep = mSqlConnect.prepareStatement(sql);
        short i = 1;
        long theTime = new java.util.Date().getTime();
        java.sql.Date theDate = getCurrentDatetime();

        prep.setInt(i++, customerID);
        prep.setInt(i++, userID);
        prep.setInt(i++, deviceID);
        prep.setInt(i++, eqID);

        //TODO: Proper implementation of Project ID
        prep.setInt(i++, 1);

        prep.setInt(i++, odo);
        prep.setString(i++, notes);
        prep.setDouble(i++, lat);
        prep.setDouble(i++, longt);
        prep.setDate(i++, theDate);
        prep.setInt(i++, pulse);
        prep.setDouble(i++, vol);



        prep.executeUpdate();
        prep.close();

        Log.d(Constants.TAG, "Insertion completed");
    }

    public void insertOrder(int customerId, int itemNumber,
                            String itemDescription, BigDecimal itemCost) throws SQLException {

        // An Insert with AceQL:

        String sql = "insert into orderlog "
                + "values ( ?, ?, ?, ?, ?, ?, ?, ?, ? )";

        // Create a new Prepared Statement

        PreparedStatement prepStatement = mSqlConnect.prepareStatement(sql);
        int i = 1;
        long theTime = new java.util.Date().getTime();
        java.sql.Date theDate = new java.sql.Date(theTime);
        Timestamp theTimestamp = new Timestamp(theTime);



        prepStatement.setInt(i++, customerId);
        prepStatement.setInt(i++, itemNumber);
        prepStatement.setString(i++, itemDescription);
        prepStatement.setBigDecimal(i++, itemCost);
        prepStatement.setDate(i++, theDate);
        prepStatement.setTimestamp(i++, theTimestamp);
        prepStatement.setBytes(i++, null);
        prepStatement.setBoolean(i++, false);
        prepStatement.setInt(i++, 1);

        prepStatement.executeUpdate();
        prepStatement.close();

        System.out.println();
        System.out.println("Insert done in orderlog.");
    }

    /**

     * An example of SELECT

     *

     * @throws SQLException

     */

    public void selectEquipment() throws SQLException {
        // a Select using AceQL:

        String sql = "select * from equipment "; //TODO: Where client id blah blah fuck

        PreparedStatement prepStat = mSqlConnect.prepareStatement(sql);
        //prepStat.setInt(1, customerId);

        ResultSet rs = prepStat.executeQuery();

        while (rs.next()) {

            int equipment_id = rs.getInt("equipment_id");
            int client_id = rs.getInt("client_id");
            String name = rs.getString("name");
            String desc = rs.getString("description");
            String make = rs.getString("make");
            String model = rs.getString("model");
            double last_odo = rs.getDouble("last_odo");
            String uom = rs.getString("uom");
            long capacity = rs.getLong("capacity");
            boolean preset = rs.getBoolean("preset");
            long preset_vol = rs.getLong("preset_vol");

            eqList.add(new EquipmentItem(equipment_id, client_id, name, desc, make, model, last_odo,
                    uom, capacity, preset, preset_vol));

        }

        prepStat.close();
        rs.close();
    }



    /**
     * Delete a Customer and its orderlog in one transaction
     *
     * @param customerId
     *            the customer id
     *
     * @throws Exception
     *             if any Exception occurs
     */
    public void deleteCustomerAndOrderLog(int customerId) throws Exception {
        // TRANSACTION BEGIN
        mSqlConnect.setAutoCommit(false);

        // We will do all our (remote) deletes in one transaction
        try {
            // 1) Delete the Customer:
            String sql = "delete from customer where customer_id = ?";
            PreparedStatement prepStatement = mSqlConnect.prepareStatement(sql);

            prepStatement.setInt(1, customerId);
            prepStatement.executeUpdate();
            prepStatement.close();

            // 2) Delete all orders for this Customer:
            sql = "delete from orderlog where customer_id = ?";
            PreparedStatement prepStatement2 = mSqlConnect.prepareStatement(sql);

            prepStatement2.setInt(1, customerId);
            prepStatement2.executeUpdate();
            prepStatement2.close();

            // Provoke an exception in order to fail. Uncomment to test:
            // if (true) throw new IllegalArgumentException("NOTHING DONE!");

            // We do either everything in a single transaction or nothing:
            mSqlConnect.commit();
            System.out.println("Ok. Commit Done on remote Server!");
        } catch (Exception e) {
            mSqlConnect.rollback();
            System.out.println("Fail. Rollback Done on remote Server!");
            throw e;
        } finally {
            mSqlConnect.setAutoCommit(true);
        }
        // TRANSACTION END
    }

    public java.sql.Date getCurrentDatetime() {
        java.util.Date today = new java.util.Date();
        return new java.sql.Date(today.getTime());
    }

    private class EquipmentItem {

        public int equipment_id;
        public int client_id;
        public String name;
        public String desc;
        public String make;
        public String model;
        public double last_odo;
        public String uom;
        public long capacity;
        public boolean preset;
        public long preset_vol;

        public EquipmentItem(){

        }

        public EquipmentItem(int equipment_id, int client_id, String name, String desc, String make,
                             String model, double last_odo, String uom, long capacity,
                             boolean preset, long preset_vol){
            this.equipment_id = equipment_id;
            this.client_id = client_id;
            this.name = name;
            this.desc = desc;
            this.make = make;
            this.model = model;
            this.last_odo = last_odo;
            this.uom = uom;
            this.capacity = capacity;
            this.preset = preset;
            this.preset_vol = preset_vol;
        }
    }
}

