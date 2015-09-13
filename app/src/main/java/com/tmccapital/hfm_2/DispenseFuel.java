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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

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
        String url = "jdbc:aceql:http://hostnamegoeshere:9090/ServerSqlManager";
        String username = "foo";
        String pwd = "bar";
        try {
            Class.forName("org.kawanfw.sql.api.client.RemoteDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        //Let's try and get the remote server
        try {
            mSqlConnect = DriverManager.getConnection(url,username,pwd);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (mSqlConnect != null){
            Log.d(Constants.TAG, "Connection Established!");
        }

        //Populate our Spinner
        spinnerArray =  new ArrayList<String>();
        spinnerArray.add("01234556");
        spinnerArray.add("000001");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eqID = (Spinner) findViewById(R.id.dispense_eq_id_spinner);

        eqID.setAdapter(adapter);

        Button go = (Button)findViewById(R.id.dispense_disp_btn);
        go.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                //Initialise all our text boxes
                EditText make = (EditText) view.findViewById(R.id.dispense_make_box);
                EditText model = (EditText) view.findViewById(R.id.dispense_model_box);
                EditText odo = (EditText) view.findViewById(R.id.dispense_odo_box);
                EditText uom = (EditText) view.findViewById(R.id.dispense_uom_box);
                EditText notes = (EditText) view.findViewById(R.id.dispense_notes_box);

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
                tmp = "C96-33.769019000000\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());
                tmp = "D85151.030926000000\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());
                tmp = "E620334567800022222\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());
                tmp = "F130L0K0A0L00000000\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());
                tmp = "H22abcdefghklmnopqr\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                //Go cmd goes here
                tmp = "K07-CMD-GOAUTH00\n";
                mUartService.writeRXCharacteristic(tmp.getBytes());

                //Let's attempt our transaction
                try {
                    sendOrder(4,1,2, Integer.parseInt(eqID.getSelectedItem().toString()),
                            Integer.parseInt( odo.getText().toString() ),
                            (short)Integer.parseInt(uom.getText().toString()),
                            13.55, 14.55, 400, 20.00,
                            (short)2,
                            notes.getText().toString());
                } catch (Exception e) {
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
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(Constants.TAG, "onPause");
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

    public void sendOrder(int customerID,int userID,int deviceID, int eqID, int odo, short uom,
                          double lat, double longt, int pulse, double vol, short vol_uom,
                          String notes) throws SQLException{
        String sql = "insert into trans_dispense " + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement prep = mSqlConnect.prepareStatement(sql);
        short i = 1;

        prep.setInt(i++, customerID);
        prep.setInt(i++, userID);
        prep.setInt(i++, deviceID);
        prep.setInt(i++, eqID);
        prep.setInt(i++, odo);
        prep.setShort(i++, uom);
        prep.setDouble(i++, lat);
        prep.setDouble(i++, longt);
        prep.setInt(i++, pulse);
        prep.setDouble(i++, vol);
        prep.setShort(i++, vol_uom);
        prep.setString(i++, notes);

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

    public void selectOrdersForCustomer(int customerId) throws SQLException {
        // a Select using AceQL:

        String sql = "select * from orderlog where customer_id = ? ";

        PreparedStatement prepStat = mSqlConnect.prepareStatement(sql);
        prepStat.setInt(1, customerId);

        ResultSet rs = prepStat.executeQuery();

        while (rs.next()) {
            int customer_id = rs.getInt("customer_id");
            int item_id = rs.getInt("item_id");
            String description = rs.getString("description");
            BigDecimal cost_price = rs.getBigDecimal("cost_price");
            Date date_placed = rs.getDate("date_placed");
            Timestamp date_shipped = rs.getTimestamp("date_shipped");
            boolean is_delivered = rs.getBoolean("is_delivered");
            int quantity = rs.getInt("quantity");

            System.out.println();
            System.out.println("customer_id : " + customer_id);
            System.out.println("item_id     : " + item_id);
            System.out.println("description : " + description);
            System.out.println("cost_price  : " + cost_price);
            System.out.println("date_placed : " + date_placed);
            System.out.println("date_shipped: " + date_shipped);
            System.out.println("is_delivered: " + is_delivered);
            System.out.println("quantity    : " + quantity);
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

}

