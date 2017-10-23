package ca.yorku.eecs.mack.demotiltballmcmaceac;

import android.app.Activity;
import android.os.Bundle;

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


    }
}
