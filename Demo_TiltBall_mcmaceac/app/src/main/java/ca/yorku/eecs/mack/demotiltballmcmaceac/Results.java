package ca.yorku.eecs.mack.demotiltballmcmaceac;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.text.DecimalFormat;

/**
 * Created by mcmaceac on 2017-10-23.
 */

public class Results extends Activity {
    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.results);

        Bundle b = getIntent().getExtras();
        int wallHits = b.getInt("wallHits");
        double aveLapTime = b.getDouble("averageLapTime");
        double inPathPercent = b.getDouble("percentInPathTime");
        int laps = b.getInt("targetLaps");

        TextView lapsTV = (TextView) findViewById(R.id.paramLaps);
        TextView lapTimeTV = (TextView) findViewById(R.id.paramAveLapTime);
        TextView wallHitsTV = (TextView) findViewById(R.id.paramWallHits);
        TextView inPathTV = (TextView) findViewById(R.id.paramInPathPercent);

        lapsTV.setText("Laps = " + laps);
        lapTimeTV.setText("Lap time = " + new DecimalFormat("##.##").format(aveLapTime) + " s (mean/lap)");
        wallHitsTV.setText("Wall hits = " + wallHits);
        inPathTV.setText("In-path time = " + new DecimalFormat("##.##").format(inPathPercent) + "%");

    }

    // called when the "Results" button is pressed
    public void clickSetup(View view) {
        Intent i = new Intent(getApplicationContext(), DemoTiltBallSetup.class);
        //i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    /** Called when the "Exit" button is pressed. */
    public void clickExit(View view) {
        super.onDestroy(); // cleanup
        this.finish(); // terminate
        //System.exit(0);
    }
}
