package ca.yorku.eecs.mack.demotiltballmcmaceac;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.content.Intent;

public class RollingBallPanel extends View
{
    final static float DEGREES_TO_RADIANS = 0.0174532925f;

    // the ball diameter will be min(width, height) / this_value
    final static float BALL_DIAMETER_ADJUST_FACTOR = 30;

    final static int DEFAULT_LABEL_TEXT_SIZE = 20; // tweak as necessary
    final static int DEFAULT_STATS_TEXT_SIZE = 10;
    final static int DEFAULT_GAP = 7; // between lines of text
    final static int DEFAULT_OFFSET = 10; // from bottom of display

    final static int MODE_NONE = 0;
    final static int PATH_TYPE_SQUARE = 1;
    final static int PATH_TYPE_CIRCLE = 2;

    final static float PATH_WIDTH_NARROW = 2f; // ... x ball diameter
    final static float PATH_WIDTH_MEDIUM = 4f; // ... x ball diameter
    final static float PATH_WIDTH_WIDE = 8f; // ... x ball diameter

    int pathType;
    float radiusOuter, radiusInner;

    Bitmap ball, decodedBallBitmap;
    int ballDiameter;

    float dT; // time since last sensor event (seconds)

    float width, height, pixelDensity;
    int labelTextSize, statsTextSize, gap, offset;

    float finishLineLeftX, finishLineLeftY, finishLineRightX, finishLineRightY;

    RectF innerRectangle, outerRectangle, innerShadowRectangle, outerShadowRectangle, ballNow, outerDetectRectangle, innerDetectRectangle, startOval;

    float pathWidth;
    boolean touchFlag;
    Vibrator vib;
    ToneGenerator tg;
    int wallHits;
    int laps, targetLaps;
    boolean lapFlag;
    boolean notCheating = true;
    boolean testingStarted = false;
    boolean firstLapStarted = false;
    boolean timeStarted = false;

    float xBall, yBall; // top-left of the ball (for painting)
    float xBallCenter, yBallCenter; // center of the ball

    double startTime;
    double startLapTime;
    double ovalStartTime, ovalInsideTime;
    double timeInside, timeOutside;
    double lapTime;
    double[] lapTimes;

    float pitch, roll;
    float tiltAngle, tiltMagnitude;
    String orderOfControl;
    float gain;
    float velocity; // in pixels/second (velocity = tiltMagnitude * tiltVelocityGain
    float dBall; // the amount to move the ball (in pixels): dBall = dT * velocity
    float xCenter, yCenter; // the center of the screen
    long now, lastT;
    Paint statsPaint, labelPaint, linePaint, fillPaint, backgroundPaint, startLinePaint, startOvalPaint;
    float[] updateY;

    public RollingBallPanel(Context contextArg)
    {
        super(contextArg);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs)
    {
        super(contextArg, attrs);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs, int defStyle)
    {
        super(contextArg, attrs, defStyle);
        initialize(contextArg);
    }

    // things that can be initialized from within this View
    private void initialize(Context c)
    {
        startLinePaint = new Paint();
        startLinePaint.setColor(Color.RED);
        startLinePaint.setStyle(Paint.Style.STROKE);
        startLinePaint.setStrokeWidth(8);
        startLinePaint.setAntiAlias(true);

        startOvalPaint = new Paint();
        startOvalPaint.setColor(Color.BLUE);
        startOvalPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(0xffccbbbb);
        fillPaint.setStyle(Paint.Style.FILL);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(DEFAULT_LABEL_TEXT_SIZE);
        labelPaint.setAntiAlias(true);

        statsPaint = new Paint();
        statsPaint.setAntiAlias(true);
        statsPaint.setTextSize(DEFAULT_STATS_TEXT_SIZE);

        // NOTE: we'll create the actual bitmap in onWindowFocusChanged
        decodedBallBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ball);

        lastT = System.nanoTime();
        this.setBackgroundColor(Color.LTGRAY);
        touchFlag = false;
        lapFlag = false;
        outerRectangle = new RectF();
        innerRectangle = new RectF();
        startOval = new RectF();
        innerShadowRectangle = new RectF();
        outerShadowRectangle = new RectF();
        outerDetectRectangle = new RectF();
        innerDetectRectangle = new RectF();
        ballNow = new RectF();
        wallHits = 0;
        laps = 0;
        lapTime = 0;
        startTime = System.currentTimeMillis();
        startLapTime = startTime;

        vib = (Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
        tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    }

    /**
     * Called when the window hosting this view gains or looses focus.  Here we initialize things that depend on the
     * view's width and height.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        if (!hasFocus)
            return;

        width = this.getWidth();
        height = this.getHeight();

        // the ball diameter is nominally 1/30th the smaller of the view's width or height
        ballDiameter = width < height ? (int)(width / BALL_DIAMETER_ADJUST_FACTOR)
                : (int)(height / BALL_DIAMETER_ADJUST_FACTOR);

        // now that we know the ball's diameter, get a bitmap for the ball
        ball = Bitmap.createScaledBitmap(decodedBallBitmap, ballDiameter, ballDiameter, true);

        // center of the view
        xCenter = width / 2f;
        yCenter = height / 2f;

        // top-left corner of the ball
        xBall = xCenter;
        yBall = yCenter;

        // center of the ball
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        // configure outer rectangle of the path
        radiusOuter = width < height ? 0.40f * width : 0.40f * height;
        outerRectangle.left = xCenter - radiusOuter;
        outerRectangle.top = yCenter - radiusOuter;
        outerRectangle.right = xCenter + radiusOuter;
        outerRectangle.bottom = yCenter + radiusOuter;

        // configure inner rectangle of the path
        // NOTE: medium path width is 4 x ball diameter
        radiusInner = radiusOuter - pathWidth * ballDiameter;
        innerRectangle.left = xCenter - radiusInner;
        innerRectangle.top = yCenter - radiusInner;
        innerRectangle.right = xCenter + radiusInner;
        innerRectangle.bottom = yCenter + radiusInner;

        // start oval that appears on the top right of the screen
        // indicating where the ball should start
        startOval.left = width - 300f;
        startOval.top = 50f;                //diameter = 250
        startOval.bottom = 300f;            //centerX = width - 175 centerY = 175
        startOval.right = width - 50f;

        // configure outer shadow rectangle (needed to determine wall hits)
        // NOTE: line thickness (aka stroke width) is 2
        outerShadowRectangle.left = outerRectangle.left + ballDiameter - 2f;
        outerShadowRectangle.top = outerRectangle.top + ballDiameter - 2f;
        outerShadowRectangle.right = outerRectangle.right - ballDiameter + 2f;
        outerShadowRectangle.bottom = outerRectangle.bottom - ballDiameter + 2f;

        // configure inner shadow rectangle (needed to determine wall hits)
        innerShadowRectangle.left = innerRectangle.left + ballDiameter - 2f;
        innerShadowRectangle.top = innerRectangle.top + ballDiameter - 2f;
        innerShadowRectangle.right = innerRectangle.right - ballDiameter + 2f;
        innerShadowRectangle.bottom = innerRectangle.bottom - ballDiameter + 2f;

        outerDetectRectangle.left = outerRectangle.left + ballDiameter / 2f;
        outerDetectRectangle.top = outerRectangle.top + ballDiameter / 2f;
        outerDetectRectangle.right = outerRectangle.right - ballDiameter / 2f;
        outerDetectRectangle.bottom = outerRectangle.bottom - ballDiameter / 2f;

        innerDetectRectangle.left = innerRectangle.left + ballDiameter / 2f;
        innerDetectRectangle.top = innerRectangle.top + ballDiameter / 2f;
        innerDetectRectangle.right = innerRectangle.right - ballDiameter / 2f;
        innerDetectRectangle.bottom = innerRectangle.bottom - ballDiameter / 2f;

        // initialize a few things (e.g., paint and text size) that depend on the device's pixel density
        pixelDensity = this.getResources().getDisplayMetrics().density;
        labelTextSize = (int)(DEFAULT_LABEL_TEXT_SIZE * pixelDensity + 0.5f);
        labelPaint.setTextSize(labelTextSize);

        statsTextSize = (int)(DEFAULT_STATS_TEXT_SIZE * pixelDensity + 0.5f);
        statsPaint.setTextSize(statsTextSize);

        gap = (int)(DEFAULT_GAP * pixelDensity + 0.5f);
        offset = (int)(DEFAULT_OFFSET * pixelDensity + 0.5f);

        lapTimes = new double[targetLaps];          //container for the lap times
        // compute y offsets for painting stats (bottom-left of display)
        updateY = new float[8]; // up to 6 lines of stats will appear
        for (int i = 0; i < updateY.length; ++i)
            updateY[i] = height - offset - i * (statsTextSize + gap);
    }

    /*
     * Do the heavy lifting here! Update the ball position based on the tilt angle, tilt
     * magnitude, order of control, etc.
     */
    public void updateBallPosition(float pitchArg, float rollArg, float tiltAngleArg, float tiltMagnitudeArg)
    {
        pitch = pitchArg; // for information only (see onDraw)
        roll = rollArg; // for information only (see onDraw)
        tiltAngle = tiltAngleArg;
        tiltMagnitude = tiltMagnitudeArg;

        // get current time and delta since last onDraw
        now = System.nanoTime();
        dT = (now - lastT) / 1000000000f; // seconds
        lastT = now;

        // don't allow tiltMagnitude to exceed 45 degrees
        final float MAX_MAGNITUDE = 45f;
        tiltMagnitude = tiltMagnitude > MAX_MAGNITUDE ? MAX_MAGNITUDE : tiltMagnitude;

        // This is the only code that distinguishes velocity-control from position-control
        if (orderOfControl.equals("Velocity")) // velocity control
        {
            // compute ball velocity (depends on the tilt of the device and the gain setting)
            velocity = tiltMagnitude * gain;

            // compute how far the ball should move (depends on the velocity and the elapsed time since last update)
            dBall = dT * velocity; // make the ball move this amount (pixels)

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            xBall += dx;
            yBall += dy;

        } else
        // position control
        {
            // compute how far the ball should move (depends on the tilt of the device and the gain setting)
            dBall = tiltMagnitude * gain;

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            xBall = xCenter + dx;
            yBall = yCenter + dy;
        }

        // make an adjustment, if necessary, to keep the ball visible (also, restore if NaN)
        if (Float.isNaN(xBall) || xBall < 0)
            xBall = 0;
        else if (xBall > width - ballDiameter)
            xBall = width - ballDiameter;
        if (Float.isNaN(yBall) || yBall < 0)
            yBall = 0;
        else if (yBall > height - ballDiameter)
            yBall = height - ballDiameter;

        // oh yea, don't forget to update the coordinate of the center of the ball (needed to determine wall  hits)
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        if (!testingStarted && ovalInsideTime >= 1000) {                   //1000 ms = 1 second
            startOvalPaint.setColor(Color.LTGRAY);      //make the start oval disappear if the user
            invalidate();                               //has been in it for more than one second
            testingStarted = true;
            tg.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
        }

        if (!testingStarted && insideStartOval()) {
            if (ovalStartTime != 0) {
                ovalInsideTime += System.currentTimeMillis() - ovalStartTime;
                ovalStartTime = System.currentTimeMillis();
            }
            else {
                ovalStartTime = System.currentTimeMillis();
            }
        }
        else if (!testingStarted && !insideStartOval()){
            ovalInsideTime = 0.0;
            ovalStartTime = 0.0;
        }
        else {      //testing has started, resume normal
            updateBallStats();
        }
        invalidate(); // force onDraw to redraw the screen with the ball in its new position
    }

    public void updateBallStats() {
        if (ballTouchingLine() && !insideBoundaries()) {
            touchFlag = true;                               //we only want a vibrate when the ball goes from inside to outside
        }
        // if ball touches wall, vibrate and increment wallHits count
        // NOTE: We also use a boolean touchFlag so we only vibrate on the first touch
        if (firstLapStarted) {      //only record stats if the first lap has started
            if (ballTouchingLine() && !touchFlag) {
                touchFlag = true; // the ball has *just* touched the line: set the touchFlag
                vib.vibrate(20); // 20 ms vibrotactile pulse
                ++wallHits;

            } else if (!ballTouchingLine() && touchFlag)
                touchFlag = false; // the ball is no longer touching the line: clear the touchFlag

            if (!timeStarted) {
                timeStarted = true;
                startTime = System.currentTimeMillis();         //the first lap and timer has started
            }

            // check to see if the ball is inside the boundaries, and update timers appropriately
            if (insideBoundaries()) {
                timeInside = System.currentTimeMillis() - startTime - timeOutside;
            } else {
                timeOutside = System.currentTimeMillis() - startTime - timeInside;
            }
            lapTime = System.currentTimeMillis() - startLapTime;

            //if notCheating is already true, we know the user has crossed the halfway point already
            notCheating = notCheating || ballCrossedHalfWayPoint();
        }
        // increase the lap count if the ball crosses the halfway point
        // we use the lapflag to know that the user is already in the bottom half
        if (ballCrossedFinishLine() && !lapFlag) {
            //Log.i("MYDEBUG", "lapTimes size = " + lapTimes.length);
            if (firstLapStarted) {

                lapFlag = true;
                tg.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                lapTimes[laps] = lapTime;
                ++laps;
                startLapTime = System.currentTimeMillis();

                // we have reached the target laps, meaning we need to show the results screen
                if (laps == targetLaps) {
                    //calculate the average lap time
                    double sum = 0.0;
                    for (int i = 0; i < targetLaps; i++) {
                        sum += lapTimes[i];
                    }
                    sum /= targetLaps;
                    sum /= 1000.0; //converting to seconds

                    //calculate the % time inside the boundaries

                    double percentInPathTime = (timeInside / (timeInside + timeOutside) * 100);

                    Bundle b = new Bundle();
                    b.putInt("wallHits", wallHits);
                    b.putDouble("averageLapTime", sum);
                    b.putDouble("percentInPathTime", percentInPathTime);
                    b.putInt("targetLaps", targetLaps);

                    Context c = getContext();
                    Intent i = new Intent(c, Results.class);
                    i.putExtras(b);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    c.startActivity(i);
                }
            }
            firstLapStarted = true;
            startLapTime = System.currentTimeMillis();
        } else if (yBallCenter < (height / 2) && lapFlag) {
            lapFlag = false;
        }
    }

    protected void onDraw(Canvas canvas)
    {
        // draw the paths
        if (pathType == PATH_TYPE_SQUARE)
        {
            // draw fills
            canvas.drawRect(outerRectangle, fillPaint);
            canvas.drawRect(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawRect(outerRectangle, linePaint);
            canvas.drawRect(innerRectangle, linePaint);
        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            // draw fills
            canvas.drawOval(outerRectangle, fillPaint);
            canvas.drawOval(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawOval(outerRectangle, linePaint);
            canvas.drawOval(innerRectangle, linePaint);
        }

        //draw the start circle
        canvas.drawOval(startOval, startOvalPaint);

        //draw the start line
        finishLineLeftX = outerRectangle.left;
        finishLineLeftY = height / 2;
        finishLineRightX = innerRectangle.left;
        finishLineRightY = height / 2;
        canvas.drawLine(finishLineLeftX, finishLineLeftY, finishLineRightX, finishLineRightY, startLinePaint);

        //draw direction arrow
        canvas.drawLine(outerRectangle.left - 30, (height / 2) - 100, outerRectangle.left - 30, (height / 2) + 100, startLinePaint);    //line down
        canvas.drawLine(outerRectangle.left - 50, (height / 2) + 80, outerRectangle.left - 30, (height / 2) + 100, startLinePaint);     //backslash
        canvas.drawLine(outerRectangle.left - 10, (height / 2) + 80, outerRectangle.left - 30, (height / 2) + 100, startLinePaint);     //forward slash

        // draw label
        canvas.drawText("Demo_TiltBall_mcmaceac", 6f, labelTextSize, labelPaint);

        // draw stats (pitch, roll, tilt angle, tilt magnitude)
        if (pathType == PATH_TYPE_SQUARE || pathType == PATH_TYPE_CIRCLE)
        {
            canvas.drawText("Lap time = " + lapTime, 6f, updateY[7], statsPaint);
            canvas.drawText("Laps = " + laps + "/" + targetLaps, 6f, updateY[6], statsPaint);
            canvas.drawText("Wall hits = " + wallHits, 6f, updateY[5], statsPaint);
            canvas.drawText("-----------------", 6f, updateY[4], statsPaint);
        }
        canvas.drawText(String.format("Tablet pitch (degrees) = %.2f", pitch), 6f, updateY[3], statsPaint);
        canvas.drawText(String.format("Tablet roll (degrees) = %.2f", roll), 6f, updateY[2], statsPaint);
        canvas.drawText(String.format("Ball x = %.2f", xBallCenter), 6f, updateY[1], statsPaint);
        canvas.drawText(String.format("Ball y = %.2f", yBallCenter), 6f, updateY[0], statsPaint);

        // draw the ball in its new location
        canvas.drawBitmap(ball, xBall, yBall, null);

    } // end onDraw

    /*
     * Configure the rolling ball panel according to setup parameters
     */
    public void configure(String pathMode, String pathWidthArg, int gainArg, String orderOfControlArg, int lapNumberArg)
    {
        // square vs. circle
        if (pathMode.equals("Square"))
            pathType = PATH_TYPE_SQUARE;
        else if (pathMode.equals("Circle"))
            pathType = PATH_TYPE_CIRCLE;
        else
            pathType = MODE_NONE;

        // narrow vs. medium vs. wide
        if (pathWidthArg.equals("Narrow"))
            pathWidth = PATH_WIDTH_NARROW;
        else if (pathWidthArg.equals("Wide"))
            pathWidth = PATH_WIDTH_WIDE;
        else
            pathWidth = PATH_WIDTH_MEDIUM;

        targetLaps = lapNumberArg;
        gain = gainArg;
        orderOfControl = orderOfControlArg;
    }

    // returns true if the ball is touching (i.e., overlapping) the line of the inner or outer path border
    public boolean ballTouchingLine()
    {
        if (pathType == PATH_TYPE_SQUARE)
        {
            ballNow.left = xBall;
            ballNow.top = yBall;
            ballNow.right = xBall + ballDiameter;
            ballNow.bottom = yBall + ballDiameter;

            if (RectF.intersects(ballNow, outerRectangle) && !RectF.intersects(ballNow, outerShadowRectangle))
                return true; // touching outside rectangular border

            if (RectF.intersects(ballNow, innerRectangle) && !RectF.intersects(ballNow, innerShadowRectangle))
                return true; // touching inside rectangular border

        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));

            if (Math.abs(ballDistance - radiusOuter) < (ballDiameter / 2f))
                return true; // touching outer circular border

            if (Math.abs(ballDistance - radiusInner) < (ballDiameter / 2f))
                return true; // touching inner circular border
        }
        return false;
    }

    // returns true if the ball crosses the finish line marked on the screen
    public boolean ballCrossedFinishLine() {
        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;

        if (yBallCenter > (height / 2)) {
            //Log.i("MYDEBUG", "yBallCenter > (height / 2)");
            //Log.i("MYDEBUG", "ballNow.intersects: " + ballNow.intersects(finishLineLeftX, finishLineLeftY, finishLineRightX, finishLineRightY));
            //Log.i("MYDEBUG", "finishLineLeftX: " + finishLineLeftX + " mid: " + finishLineLeftY + " finishLineRightX: " + finishLineRightX);
            //check to see if the ball is between the line boundaries and if the user has at least
            //crossed the half way point
            if (notCheating && ballNow.intersects(finishLineLeftX, finishLineLeftY, finishLineRightX, finishLineRightY)) {
                notCheating = false;        //user must cross half way point in order to set this to true
                return true;
            }
            else
                return false;
        }
        else
            return false;
    }

    //used to detect cheating (user must pass halfway point to register a lap
    public boolean ballCrossedHalfWayPoint() {
        if (yBallCenter < (height / 2)) {
            //check to see if the ball is between the line boundaries and if the user has at least
            //crossed the half way point
            if (ballNow.intersects(xCenter, finishLineLeftY, width, finishLineRightY)) {
                return true;
            } else
                return false;
        } else
            return false;
    }

    public boolean insideBoundaries() {
        if (pathType == PATH_TYPE_SQUARE) {
            ballNow.left = xBall;
            ballNow.top = yBall;
            ballNow.right = xBall + ballDiameter;
            ballNow.bottom = yBall + ballDiameter;

            if (RectF.intersects(ballNow, outerDetectRectangle) && !RectF.intersects(ballNow, innerDetectRectangle)) {
                return true;
            }
        }
        else if (pathType == PATH_TYPE_CIRCLE) {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));

            // pythagorean theorem: if < r, it is in circle. if > r, outside circle
            if (Math.abs(ballDistance) < (radiusOuter) && Math.abs(ballDistance) > (radiusInner))
                return true;
        }
        return false;
    }

    public boolean insideStartOval() {
        float startCenterX = width - 175;
        float startCenterY = 175;
        final float ballDistance = (float)Math.sqrt((xBallCenter - startCenterX) * (xBallCenter - startCenterX)
                + (yBallCenter - startCenterY) * (yBallCenter - startCenterY));

        if (Math.abs(ballDistance) < 125) { //if the ball distance is less than the radius of start
            return true;
        }
        return false;
    }
}
