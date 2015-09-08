package com.tmccapital.hfm_2;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DispenseFuel extends AppCompatActivity {

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    private Spinner eqID;
    private Button scan_btn;
    private String mConnectedDeviceName = null;
    private String mChosenAddress;
    private ArrayAdapter<String> mConversationArrayAdapter;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothService mBluetoothService;
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
                transInfo = transInfo + "Local Transaction ID: #####\n"; //Given by Arduino
                transInfo = transInfo + "Remote Transaction ID: ###\n"; //Given by DB
                transInfo = transInfo + "Equipment ID: " + eqID.getSelectedItem().toString() + "\n";
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
                String tmp = "A45" + "000001" + "0000123456";
                mBluetoothService.write(tmp.getBytes());
                tmp = "B310000123400005678";
                mBluetoothService.write(tmp.getBytes());
                tmp = "C96-33.769019000000";
                mBluetoothService.write(tmp.getBytes());
                tmp = "D85151.030926000000";
                mBluetoothService.write(tmp.getBytes());
                tmp = "E620A34567800022222";
                mBluetoothService.write(tmp.getBytes());
                tmp = "F130L0K0A0L00000000";
                mBluetoothService.write(tmp.getBytes());
                tmp = "H22abcdefghklmnopqr";
                mBluetoothService.write(tmp.getBytes());


                //Let's attempt our transaction
                try {
                    sendOrder(4,1,2, Integer.parseInt(eqID.getSelectedItem().toString()),
                            Integer.parseInt( odo.getText().toString() ),
                            (short)Integer.parseInt(uom.getText().toString()),
                            13.55, 14.55, 400, 20.00,
                            (short)2,
                            notes.getText().toString());
                } catch (SQLException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),"Failed  to push data to SQL Server",
                            Toast.LENGTH_SHORT).show();
                }


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

