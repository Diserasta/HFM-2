package com.tmccapital.hfm_2;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class DispenseSpinner extends AppCompatActivity {

    private UartService mUartService;
    private ArrayAdapter<String> listAdapter;
    private ArrayList<String> numList = null;
    private TextView numbers;
    //private final Handler h=new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            numList = extras.getStringArrayList("nums");
        }
        setContentView(R.layout.activity_dispense_spinner);

        //Intent bindIntent = new Intent(this, UartService.class);
        //bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        numbers = (TextView) findViewById(R.id.disp_spinner_num);

        Button log = (Button)findViewById(R.id.disp_spinner_done);
        log.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DispenseSpinner.this, MainActivity.class);
                DispenseSpinner.this.startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_dispense_spinner, menu);
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

    private void updateUi(String in){

        numbers.setText(in);
    }
}
