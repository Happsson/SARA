package nu.geeks.sara;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.TextView;


public class SaraMain extends Activity {


    /*
    The app is locked in portrait mode. This is done in AndroidManifest.xml with the lines:
            android:screenOrientation="portrait"
            android:configChanges="keyboardHidden|orientation"

     The titlebar (where the name of the app is, if you look in the XML-viewer, is also removed
     in the manifest with this line:
             android:theme="@android:style/Theme.DeviceDefault.Light.NoActionBar.Fullscreen"

     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sara_main);

        /*
        Everything created in the XML-view (drag and drop view) must be linked in code to be useful.
        "textView" in R.id.textView is the name given in XML-view.
        Must be final.
         */
        final TextView text1 = (TextView) findViewById(R.id.textView);
        final SeekBar bar = (SeekBar) findViewById(R.id.seekBar);

        //Set initial value to 50, middle of seekbar.
        bar.setProgress(50);

        /*
        Set initial text. Can just as easily be done in the XML-view or strings.xml (just a
        weird way to set final strings for buttons en texts and such.)
        */
        text1.setText("Speed: 0");

        /*
        rotate text to 90 degs. Could have set the orientation of the app to be landscape
        instead, but fudge it
        */
        text1.setRotation(90);

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
                text1.setText("Speed: " + (progress - 50));
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
}
