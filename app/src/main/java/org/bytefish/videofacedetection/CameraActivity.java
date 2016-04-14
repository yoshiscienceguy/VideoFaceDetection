// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package org.bytefish.videofacedetection;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;


public class CameraActivity extends IOIOActivity
        implements SurfaceHolder.Callback {
    public TextView status;
    private final int pan_pin = 46;
    private final int tilt_pin = 45;
    private final int freq = 100;
    public SeekBar seekBarX;
    public SeekBar seekBarY;
    private int curX = 500;
    private int curY = 500;
    private int limitX = 700;
    private int limitY = 1200;
    private int percisionX = 200;
    private int percisionY = 300;
    private int speedX = 5;
    private int speedY = 2;
    private float[] coords = {0.1f,0.1f};

    public static final String TAG = CameraActivity.class.getSimpleName();

    private Camera mCamera;

    // We need the phone orientation to correctly draw the overlay:
    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    private int mDisplayOrientation;

    // Holds the Face Detection result:
    private Camera.Face[] mFaces;

    // The surface view for the camera data
    private SurfaceView mView;

    // Draw rectangles and other fancy stuff:
    private FaceOverlayView mFaceView;

    // Log all errors:
    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();



    /**
     * Sets the faces for the overlay view, so it can be updated
     * and the face overlays will be drawn again.
     */
    private FaceDetectionListener faceDetectionListener = new FaceDetectionListener() {
        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            Log.d("onFaceDetection", "Number of Faces:" + faces.length);
            // Update the view now!
            mFaceView.setFaces(faces);



        }
    };


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        //mView = new SurfaceView(this);
        //setContentView(mView);
        // Now create the OverlayView:

        seekBarX = (SeekBar) findViewById(R.id.SeekBar1);
        seekBarY = (SeekBar) findViewById(R.id.SeekBar2);
        status = (TextView) findViewById(R.id.stat);
        mView = (SurfaceView) findViewById(R.id.feed);
        seekBarX.setProgress(500);
        seekBarY.setProgress(500);
        status.setTextColor(Color.RED);
        mFaceView = new FaceOverlayView(this);

        addContentView(mFaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Create and Start the OrientationListener:
        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();

        enableUi(false);

    }


    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SurfaceHolder holder = mView.getHolder();
        holder.addCallback(this);
    }


    protected void onPause() {
        mOrientationEventListener.disable();
        super.onPause();
    }


    protected void onResume() {
        mOrientationEventListener.enable();
        super.onResume();
    }


    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    mCamera = Camera.open(camIdx);

                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }
        mCamera.setFaceDetectionListener(faceDetectionListener);
        mCamera.startFaceDetection();
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }


    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        // We have no surface, return immediately:
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Try to stop the current preview:
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // Ignore...
        }

        configureCamera(width, height);
        setDisplayOrientation();
        setErrorCallback();

        // Everything is configured! Finally start the camera preview again:
        mCamera.startPreview();
    }

    private void setErrorCallback() {
        mCamera.setErrorCallback(mErrorCallback);
    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, 0);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void configureCamera(int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        // Set the PreviewSize and AutoFocus:
        //parameters.setFlashMode("off");
        //parameters.set("camera-id",2);
        //parameters.setPreviewSize(640,480);

        setOptimalPreviewSize(parameters, width, height);
        //setAutoFocus(parameters);
        // And set the parameters:
        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters, int width, int height) {
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        float targetRatio = (float) width / height;
        Camera.Size previewSize = Util.getOptimalPreviewSize(this, previewSizes, targetRatio);
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }


    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.setPreviewCallback(null);
        mCamera.setFaceDetectionListener(null);
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }

    /**
     * We need to react on OrientationEvents to rotate the screen and
     * update the views.
     */
    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }


        public void onOrientationChanged(int orientation) {


            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CameraActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mFaceView.setOrientation(mOrientationCompensation);
            }
        }
    }
    class Looper extends BaseIOIOLooper {

        private PwmOutput pan;
        private PwmOutput tilt;


        @Override
        public void setup() throws ConnectionLostException {

            pan = ioio_.openPwmOutput(pan_pin, freq);
            tilt = ioio_.openPwmOutput(tilt_pin, freq);
            status.setText("Connected!");
            pan.setPulseWidth(500 + 500 * 2);
            tilt.setPulseWidth(500 + 1000 * 2);
            //enableUi(true);
        }

        @Override
        public void loop() throws ConnectionLostException, InterruptedException {
            //status.setText("yo");

            setNumber();

            pan.setPulseWidth(500 + seekBarX.getProgress() * 2);

            tilt.setPulseWidth(500 + seekBarY.getProgress() * 2);
            Thread.sleep(10);


        }

        @Override
        public void disconnected() {
            enableUi(false);
        }
    }

    @Override
    protected IOIOLooper createIOIOLooper() {
        return new Looper();
    }
    private void enableUi(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekBarX.setEnabled(enable);
                seekBarY.setEnabled(enable);
            }
        });
    }
    private void setNumber() {
        coords = new float[]{0, 0};
        mFaceView.faceCoords(coords);
        if(coords[0] > limitX + percisionX && coords[0] != 0){
            curX -= speedX;

        }
        if(coords[0] < limitX - percisionX && coords[0] != 0){
            curX += speedX;
        }
        if(coords[1] > limitY + percisionY && coords[1] != 0){
            curY -= speedY;

        }
        if(coords[1] < limitY - percisionY && coords[1] != 0){
            curY += speedY;
        }
        if(curX > 1000){
            curX = 500;
        }
        if(curX < 1){
            curX = 0;
        }
        if(curY > 1000){
            curY = 1000;
        }
        if(curY < 100){
            curY = 100;
        }

        seekBarX.setProgress(curX);
        seekBarY.setProgress(curY);
        final String out = "X : "+ String.valueOf(coords[0]) +", Y :" + String.valueOf(coords[1]);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(out);
            }
        });
    }
}