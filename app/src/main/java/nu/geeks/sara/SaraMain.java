package nu.geeks.sara;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.TooManyListenersException;
import java.util.UUID;


/*
The app is locked in portrait mode. This is done in AndroidManifest.xml with the lines:
        android:screenOrientation="portrait"
        android:configChanges="keyboardHidden|orientation"

 The titlebar (where the name of the app is, if you look in the XML-viewer, is also removed
 in the manifest with this line:
         android:theme="@android:style/Theme.DeviceDefault.Light.NoActionBar.Fullscreen"

 */


public class SaraMain extends Activity implements SensorEventListener {


    private static String address; //This string will hold the name of our bluetooth-device

    Handler bluetoothIn;

    final int handlerState = 0;                        //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();
    private ConnectedThread mConnectedThread;


    //double acc[] = new double[3];
    double accY;
    private SensorManager sensorManager;
    TextView text1, text2, tConnected;

    Boolean bluetoothEnable = true;
    Boolean bluetoothConnected = false;


    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private final int REQUEST_ENABLE_BT = 1234;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sara_main);

        //Initializing sensor, creating a sensor manager.
        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

        /*
        Everything created in the XML-view (drag and drop view) must be linked in code to be useful.
        "textView" in R.id.textView is the name given in XML-view.
        Must be final.
         */
        tConnected = (TextView) findViewById(R.id.tConnected);
        text1 = (TextView) findViewById(R.id.textView);
        text2 = (TextView) findViewById(R.id.textView2);
        final SeekBar bar = (SeekBar) findViewById(R.id.seekBar);

        //Set initial value to 50, middle of seekbar.
        bar.setProgress(50);

        /*
        Set initial text. Can just as easily be done in the XML-view or strings.xml (just a
        weird way to set final strings for buttons en texts and such.)
        */
        text1.setText("Speed: 0");


        /*
        OnSeekBarChangeListener will be called upon when the user touch the seekbar.
        This is interrupt based, will be called automagically.
         */
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                /*
                Simply change the textvalue to the current progress.
                 */
                text1.setText("Speed: " + (progress - 50)/5);
                if(bluetoothConnected) {
                    mConnectedThread.write(Integer.toString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //if user lets go of gas pedal, speed is set to 0.
                seekBar.setProgress(50);
            }
        });

    }

    /**
     * Initiate bluetooth connection with SARA. If SARA and the phone is not paired, it will promt
     * a message to the user, telling hen to connect to SARA manually. 
     *
     */
    private void bluetoothConnection(){

        //Initialize bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null){
            //If bluetoothadapter == null, the device does not support bluetooth.
            bluetoothEnable = false;
            Toast.makeText(getApplicationContext(), "Bluetooth not supported!", Toast.LENGTH_LONG).show();

        }

        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                if(device.getName().equals("HC-06")){
                    address = device.getAddress();
                    Toast.makeText(getApplicationContext(), "CONNECTED TO SARA!", Toast.LENGTH_LONG).show();
                    tConnected.setText("Connected");
                    tConnected.setTextColor(Color.GREEN);
                    bluetoothConnected = true;
                }
                }
            }

        if(pairedDevices.size() == 0){
            new AlertDialog.Builder(SaraMain.this)
                    .setTitle("No connection found")
                    .setMessage("You are not paired with SARA. Do this by manually " +
                            "connecting to the bluetooth receiver in" +
                            " your bluetooth settings. Then start " +
                            "this app and try again. App will close now.")
                    .setPositiveButton("Got it!", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();

        }
        bluetoothConnected = false;
        }



    @Override
    public void onResume() {
        super.onResume();

        if(!bluetoothConnected) {
            bluetoothConnection();
        }
        if(bluetoothConnected) {
            //Get MAC address from DeviceListActivity via intent
            Intent intent = getIntent();

            //Get the MAC address from the DeviceListActivty via EXTRA
            //create device and set the MAC address
            BluetoothDevice device = btAdapter.getRemoteDevice(address);

            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
            }
            // Establish the Bluetooth socket connection.
            try {
                btSocket.connect();
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    //insert code to deal with this
                }
            }
            mConnectedThread = new ConnectedThread(btSocket);
            mConnectedThread.start();
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    /* This has to be defined when implementing SensorEventListener.
    *   This is the method that is called when the sensor values changes (which is pretty much
    *   all the time). Also interrupt-based, does not need to be called upon.
    *
    */
    public void onSensorChanged(SensorEvent event){
      if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){

          /*
          for(int i = 0; i < 3; i++){
              acc[i] = event.values[i];
          }
          */

          //realised only value[1] is of use. That is the acc on the y-axis.
          if(event.values[1] < -0.5f){
              text2.setText("L");
              if(bluetoothConnected) {
                  mConnectedThread.write(Float.toString(event.values[1]));
              }
          }else if(event.values[1] > 0.5f){
              if(bluetoothConnected) {
                  mConnectedThread.write(Float.toString(event.values[1]));
              }
              text2.setText("R");
          }else{
              text2.setText("N");
          }

          //Just needed some way to show amount of tilting. The letter R or L will tilt with the screen
          text2.setRotation(event.values[1]*-5+90);


      }


    }

    //Has to be defined when implementing sensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy){}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                Toast.makeText(getApplicationContext(), "Bluetooth enabled!", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(getApplicationContext(), "Bluetooth NOT enabled!", Toast.LENGTH_LONG).show();
            }
        }

    }

/*
    Stuff below is auto generated code. Don't think we need to care about it.
     */



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sara_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            bluetoothConnection();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


//create new class for connect thread
private class ConnectedThread extends Thread {
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    //creation of the connect thread
    public ConnectedThread(BluetoothSocket socket) {
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            //Create I/O streams for connection
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
        byte[] buffer = new byte[256];
        int bytes;

        // Keep looping to listen for received messages
        while (true) {
            try {
                bytes = mmInStream.read(buffer);            //read bytes from input buffer
                String readMessage = new String(buffer, 0, bytes);
                // Send the obtained bytes to the UI Activity via handler
                bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
            } catch (IOException e) {
                break;
            }
        }
    }
    //write method
    public void write(String input) {
        byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
        try {
            mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
        } catch (IOException e) {
            //if you cannot write, close the application
            Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
            bluetoothConnected = false;
            tConnected.setText("Not connected!");
            tConnected.setTextColor(Color.RED);
        }
    }
}
}

