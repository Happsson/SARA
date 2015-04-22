package nu.geeks.sara;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
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

    Button horn;

    final int handlerState = 0;                        //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private ConnectedThread mConnectedThread;

    char hornSend = 0;

    LinearLayout steeringSettings;


    int steeringCorrectionValue = 0;
    int steeringMaxValue = 16;

    private SensorManager sensorManager;
    TextView tConnected, tSettingMax, tSteeringCorr;

    private char gasPosition = 50;

    boolean sendAllowed;

    boolean bluetoothEnable = true;
    boolean bluetoothConnected = false;
    boolean steeringSettingsVisible = false;



    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private final int REQUEST_ENABLE_BT = 1234;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sara_main);

        sendAllowed = true;

        horn = (Button) findViewById(R.id.button);

        //Initializing sensor, creating a sensor manager.
        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);

        /*
        Everything created in the XML-view (drag and drop view) must be linked in code to be useful.
        "textView" in R.id.textView is the name given in XML-view.
        Must be final.
         */
        tConnected = (TextView) findViewById(R.id.tConnected);
        tSettingMax = (TextView) findViewById(R.id.tMaxSteering);
        tSteeringCorr = (TextView) findViewById(R.id.tSteering);

        steeringSettings = (LinearLayout) findViewById(R.id.steeringSettings);

        final SeekBar bar = (SeekBar) findViewById(R.id.seekBar);

        setSeekbars();


        //Set initial value to 50, middle of seekbar.
        bar.setProgress(50);

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
                gasPosition = (char) progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //if user lets go of gas pedal, speed is set to 0.
                gasPosition = 50;
                seekBar.setProgress(50);
            }
        });


        horn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    hornSend = 1;
                }
                if(event.getAction() == MotionEvent.ACTION_UP){
                    hornSend = 0;
                }
                return false;
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

            //if the bluetooth adapter is not enabled, create a new intent that enables it,
            //and listen for the answer in onActivityForResult();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        // If there are paired devices
        if (pairedDevices.size() > 0) {

            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {

                if(device.getName().equals("HC-06")){
                    //If the paired device is HC-06, SARA is connected. Else ignore.
                    address = device.getAddress();
                   // Toast.makeText(getApplicationContext(), "CONNECTED TO SARA!", Toast.LENGTH_LONG).show();
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
            bluetoothConnected = false;
        }
    }



    @Override
    public void onResume() {
        super.onResume();

        sendAllowed = true;

        if(!bluetoothConnected) {
            //If bluetooth is not connected when app starts, try and connect it.
            bluetoothConnection();
        }
        if(bluetoothConnected) {
            bluetoothStep2();
        }

    }

    private void bluetoothStep2() {
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

          char sendSteering = convertAcc(event.values[1]);
          if(bluetoothConnected) mConnectedThread.write(sendSteering, gasPosition, hornSend);

      }


    }

    private char convertAcc(float in){
        char c;
        char ret;
        float ut = in*2f;
        int utInt = (int) ut;

        ret = (char) (85+steeringCorrectionValue-(utInt * (steeringMaxValue/10f)));

        return ret;
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

            btAdapter = null;
            btSocket = null;
            mConnectedThread = null;
            bluetoothConnection();
            bluetoothStep2();
            return true;
        }
        if(id == R.id.finetune){
            steeringSettingsVisible = ! steeringSettingsVisible;
            if(steeringSettingsVisible) steeringSettings.setVisibility(View.VISIBLE);
            if(!steeringSettingsVisible) steeringSettings.setVisibility(View.INVISIBLE);
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void setSeekbars() {

        final SeekBar corrections = (SeekBar) findViewById(R.id.sSteeringCorr);
        final SeekBar steeringMax = (SeekBar) findViewById(R.id.sSteeringMax);

        corrections.setProgress(steeringCorrectionValue + 20);
        steeringMax.setProgress(steeringMaxValue);




        corrections.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                steeringCorrectionValue = progress-20;
                if((progress-20)>0) {
                    tSteeringCorr.setText("Steering correction: +" + (progress - 20));
                }else{
                    tSteeringCorr.setText("Steering correction: " + (progress - 20));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        steeringMax.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                steeringMaxValue = progress;

                tSettingMax.setText("Steering max * " + (progress/10f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


    }


    @Override
    protected void onStop() {
        super.onStop();
        //If app is exited, stop car.
        mConnectedThread.write((char) convertAcc(0), (char) 50, (char) 0);
        sendAllowed = false;
        finish();
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
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "No socket", Toast.LENGTH_LONG).show();
        }

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
    public void write(char steering, char gas, char horn) {
        byte[] msgBuffer = {(byte) steering, (byte) gas, (byte) horn};
        boolean sent = true;

        if(sendAllowed){

            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream

            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Couldn't send to bluetooth", Toast.LENGTH_LONG).show();
                bluetoothConnected = false;
                tConnected.setText("Not connected!");
                tConnected.setTextColor(Color.RED);
                sent = false;
            }
            if (sent) {
                tConnected.setText("" + (int) steering + " , " +(int) gas + " , " + (int) horn);
                tConnected.setTextColor(Color.GREEN);
            }

        }

    }

}
}

