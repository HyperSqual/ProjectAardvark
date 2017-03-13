/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompatBase;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.KeyEvent;
import android.view.InputEvent;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;

import java.io.IOException;
import java.security.AccessControlContext;
import java.util.ArrayList;

import static com.google.android.gms.samples.vision.face.facetracker.R.raw.success;
import static java.security.AccessController.getContext;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";
    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    private int trackerMode = 3;    //0 : Gradient clear to center.
                                    // 1 : Continuous left, Beeps Right.
                                    // 2 : Gradient continous left, gradient beeps right
                                    // 3 : Only vibrate gradient when A or S is pressed
    private int trackerModeMax = 3;
    private int countMode = 0; //boolean for count mode
    private int isAPressed;
    private int isSPressed;

    TextToSpeech tts;
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) { //catches keypresses
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:        //when volume up is pressed..
                if (action == KeyEvent.ACTION_DOWN) {
                    increaseTrackerMode();          //switch to next trackermode
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) { //when volume down is pressed..
                    toggleCountMode();
                }
            case KeyEvent.KEYCODE_A:
                if(action == KeyEvent.ACTION_DOWN) {
                    isAPressed = 1;
                }
                if(action == KeyEvent.ACTION_UP) {
                    isAPressed = 0;
                }
            case KeyEvent.KEYCODE_S:
                if(action == KeyEvent.ACTION_DOWN) {
                    isSPressed = 1;
                }
                if(action == KeyEvent.ACTION_UP) {
                    isSPressed = 0;
                }
            default:
            return super.dispatchKeyEvent(event);
            }
            //return true;
            //gotta find out how to make this work
        }



    public void toggleCountMode(){  //toggle if count mode is on
        if(countMode == 0){         //if count mode is off
            mCameraSource.release();
            countMode = 1;          //turn it on
            createCameraSource();   //recreate cam source
            startCameraSource();
        }
        else if(countMode == 1){ //if count mode on
            mCameraSource.release();
            countMode = 0;       //turn it off
            createCameraSource();//recreate cam source
            startCameraSource();
        }

    }
    public int increaseTrackerMode() { //simple method to count to max, then start over
        if (trackerMode < trackerModeMax){
            trackerMode++;
        }
        else
            trackerMode = 0;

        return trackerMode;
    }

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
        tts=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                tts.setLanguage(Locale.UK);
            }
        });

    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        if(countMode == 0) {
            detector.setProcessor(
                    new LargestFaceFocusingProcessor.Builder(detector, new GraphicFaceTracker(mGraphicOverlay))
                            .build());
        }
        if(countMode == 1) {
            detector.setProcessor(
                    new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                            .build());
        }

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource= new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(720, 480)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(60.0f)
                .build();

    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        if(tts !=null){
            tts.stop();
            tts.shutdown();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
        if(tts !=null){
            tts.stop();
            tts.shutdown();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }

    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;


        Context context=getApplicationContext();

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }
        private long lastTimeVibrated = 0; //counter to time vibrations
        private long waitTime = 10000L;
        /**
         * Update the position/characteristics of the face within the overlay.
         */
        Vibrator v = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            float centerx=0;    //coordinated of the calculated center;
            float centery=0;
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) { //in landscape mode...
                centerx = mFaceGraphic.translateX(mCameraSource.getPreviewSize().getWidth()/2); //use half of the preview size to get the middle
                centery = mFaceGraphic.translateY(mCameraSource.getPreviewSize().getHeight()/2);
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) { //in portrait mode..
                centery = mFaceGraphic.translateX(mCameraSource.getPreviewSize().getWidth()/2); //switch x and y, for rotated phone
                centerx = mFaceGraphic.translateY(mCameraSource.getPreviewSize().getHeight()/2);
            }
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            float x = mFaceGraphic.translateX(face.getPosition().x + face.getWidth() / 2);
            float y = mFaceGraphic.translateY(face.getPosition().y + face.getHeight() / 2);

            double targetDistance = 30;
            double distanceFromCenter = Math.sqrt(Math.pow((centerx - x),2) + Math.pow((centery-y),2));
            double distanceFromCenterX = Math.abs(centerx - x);
//            Log.d("AA", "centerx:"+Float.toString(centerx)+" "
//                    + "centery:"+Float.toString(centery)+" "
//                    + "x:"+Float.toString(x)+" "
//                    + "y:"+Float.toString(y));


            if(countMode==0) { //only do all this stuff if countmode is OFF
                long[] pattern = {0, 100, 200}; //pattern for beeping
                long[] patternG = {(long) (0),  //start from ms 0,
                        (long) (distanceFromCenterX / 2),//vibrate for this amount of ms,
                        (long) (distanceFromCenterX),}; //silence for this amount of ms
                long vibrateDelay = 300L;       //300 ms delay between vibrations
                long vibrateDelayG = patternG[0] + patternG[1] + patternG[2]; //delay based on duration of vibrations

                //Tracker modes!
                if (trackerMode == 0) {                 //tracker mode 0: gradient, disappearing in middle
                    if ((int) distanceFromCenter > targetDistance) {
                        v.vibrate((long) distanceFromCenter / 10);
                    } else if (distanceFromCenter < targetDistance) { //when we are close enough
                        playSound(); //play a sound
                        v.cancel(); //stop vibrating
                    }
                } else if (trackerMode == 1) {           //tracker mode 1: left continuous, right periodically
                    if (distanceFromCenterX > targetDistance) { //if distance from center is greater than 20 px..
                        if (x < centerx) {          //if face is on the left of center..
                            v.vibrate((long) 20);   // vibrate continuously
                        }
                        if (x > centerx && (System.currentTimeMillis() - lastTimeVibrated > vibrateDelay)) {   //if face is on the right of center
                            lastTimeVibrated = System.currentTimeMillis();
                            v.vibrate(pattern, 0);  // vibrate according to pattern

                        }
                    } else if (distanceFromCenterX < targetDistance) { //close enough
                        playSound(); //play sound
                        v.cancel(); //stop vibrating
                    }

                } else if (trackerMode == 2) {            //tracker mode 2: gradient continuous, gradient periodically
                    if (distanceFromCenterX > targetDistance) { //if distance from center is greater than 20 px..
                        if (x < centerx) {          //if face is on the left of center..
                            v.vibrate((long) distanceFromCenterX / 10);   // vibrate continuously
                        }
                        if (x > centerx && (System.currentTimeMillis() - lastTimeVibrated > vibrateDelayG)) {   //if face is on the right of center
                            lastTimeVibrated = System.currentTimeMillis();
                            v.vibrate(patternG, 0);  // vibrate according to pattern
                        }
                    } else if (distanceFromCenterX < targetDistance) {
                        playSound(); //play sound
                        v.cancel(); //stop vibrating
                    }
                } else if(trackerMode == 3) { // tracker mode 3: only vibrate when A or S is pressed
                    if (distanceFromCenterX > targetDistance) { //if distance from center is greater than 20 px..
                        if (isAPressed == 1) {
                            if (x < centerx) {          //if face is on the left of center..
                                v.vibrate((long) distanceFromCenterX / 10);   // vibrate continuously
                            }
                        }

                        if (isSPressed == 1) {
                            if (x > centerx) {          //if face is on the left of center..
                                v.vibrate((long) distanceFromCenterX / 10);   // vibrate continuously
                            }
                        }
                    }
                }

            }

            else if(countMode==1){ // if count mode is on...!



                    int amountFaces = detectionResults.getDetectedItems().size(); //count all faces
                    ArrayList<Integer> patternList = new ArrayList<Integer>();
                    patternList.add(100); //add delay before beginning
                    for(int i=0; i < amountFaces; i++){ //loop and add beeps and pauses
                        patternList.add(100);
                        patternList.add(200);
                    }
                    patternList.add(0);                 //add final beep
                    patternList.add(2000);              //add final pause

                    //convert ArrayList to array:
                    long[] pattern = new long[patternList.size()]; //create array size of list
                    for (int i=0; i < pattern.length; i++)         //loop and add every element
                    {
                        pattern[i] = patternList.get(i).intValue();
                    }


                    int totalTime = 100 + 300 * amountFaces + 2000 + 8000; //calculate total time
                    // vibrate for created pattern once



                    tts.setLanguage(Locale.US);
                    if(!tts.isSpeaking()&& System.currentTimeMillis() - lastTimeVibrated > totalTime) {
                        tts.speak("I see " + amountFaces + ",faces.", TextToSpeech.QUEUE_FLUSH, null);
                        v.vibrate(pattern, -1);
                        lastTimeVibrated = System.currentTimeMillis();
                    }



            }
        }






        public void playSound(){ //play a cash sound !
            final MediaPlayer mediaPlayer = new MediaPlayer();
            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/"+R.raw.success);
            try {
                mediaPlayer.setDataSource(context, uri);

            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
            try {
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
            v.cancel();
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
            v.cancel();
        }

    }
}
