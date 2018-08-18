package iot.com.wastebin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class Camera extends Activity implements SurfaceHolder.Callback{

    // Consts
    public static final String CAMERA_ID = "0";
    public static final int CAMERA_REQUEST_CODE = 1;
    public static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    public static final String[] PERMISSIONS = {CAMERA_PERMISSION};
    public static final Size IMAGE_SIZE = new Size(225, 255);
    public static final String CLASSIFICATION_RESULT_INTENT_KEY = "objectInImage";
    public static final String IMAGE_CAPTURED_INTENT_KEY = "captureImage";

    // Vars
    private CameraManager mCameraManager;
    private CameraDevice mCamera;
    private CameraCaptureSession mCaptureSession;
    private Surface mStreamSurface;
    private CaptureRequest streamCaptureRequest;
    private CameraCaptureSession.CaptureCallback streamCaptureListener;
    private CaptureRequest stillCaptureRequest;
    private CameraCaptureSession.CaptureCallback stillCaptureListener;
    private ImageReader imgReader;
    private ImageReader.OnImageAvailableListener imgReaderListener;

    // Views
    private SurfaceView streamSurface;

    // Model
    private ImageClassifier imageClassifier;
    private int currentScreenRotation;
    private Size previewSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        initViews();
        initCamera();
        initModels();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateCurrentScreenRotation();
    }

    private void updateCurrentScreenRotation(){
        currentScreenRotation = getWindowManager().getDefaultDisplay().getRotation();
    }

    private void initImageReaderListener(){
        imgReaderListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                ByteBuffer imageBuffer = image.getPlanes()[0].getBuffer();
                byte[] imageData = new byte[imageBuffer.remaining()];
                imageBuffer.get(imageData);
                image.close();

                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                bitmap = fixImageRotation(bitmap);
                String objectInImage = imageClassifier.classify(bitmap);

                Intent i = new Intent(getApplicationContext(), ResultDisplay.class);
                i.putExtra(IMAGE_CAPTURED_INTENT_KEY, bitmap);
                i.putExtra(CLASSIFICATION_RESULT_INTENT_KEY, objectInImage);
                startActivity(i);
            }
        };
    }

    private Bitmap fixImageRotation(Bitmap image){
        image = rotateBitmap(image, 90*(1-currentScreenRotation));
        return image;
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void initViews(){
        streamSurface = findViewById(R.id.streamSurface);
        streamSurface.getHolder().addCallback(this);
    }

    private void initModels(){
        imageClassifier = new ImageClassifier(getAssets());

        imgReader = ImageReader.newInstance(IMAGE_SIZE.getWidth(), IMAGE_SIZE.getHeight(), ImageFormat.JPEG, 1);
        initImageReaderListener();
        imgReader.setOnImageAvailableListener(imgReaderListener, null);

        previewSize = new Size(0, 0);
    }

    private void initCamera(){
        mCameraManager = getSystemService(CameraManager.class);
        try {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(CAMERA_ID);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
        } catch (CameraAccessException e) {
            Log.e("Camera", "Cannot access Camera");
            e.printStackTrace();
        }
    }

    private void openCamera(){
        if (mCamera != null) return;
        CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.i("Camera", "Camera Opened");
                mCamera = camera;
                establishCaptureSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Log.i("Camera", "Disconnected. Camera is null now.");
                mCamera = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Log.e("Camera", "Error. Camera is null now.");
                mCamera = null;
            }
        };
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                mCameraManager.openCamera(CAMERA_ID, cameraCallback, null);
            } else {
                requestPermissions(PERMISSIONS, CAMERA_REQUEST_CODE);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            openCamera();
        } else {
            Log.i("Camera", "Permission not Granted for Camera.");
        }
    }

    public void establishCaptureSession(){
        List<Surface> surfaceList = new LinkedList<>();
        // TODO: Configure the previewSurface to match the Size and Format
        Surface previewSurface = streamSurface.getHolder().getSurface();
        Surface captureSurface = imgReader.getSurface();
        surfaceList.add(previewSurface);
        surfaceList.add(captureSurface);
        CameraCaptureSession.StateCallback captureCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.i("Camera", "Capture Session established");
                mCaptureSession = session;
                updatePreview(null);
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.e("Camera", "Failed to open CaptureSession");
            }
        };
        try {
            mCamera.createCaptureSession(surfaceList, captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void updatePreview(View view) {
        if (mCaptureSession == null) return;
        try {
            if (streamCaptureRequest == null || streamCaptureListener == null){
                initPreviewRequest();
            }
            try {
                mCaptureSession.setRepeatingRequest(streamCaptureRequest, streamCaptureListener, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            mCaptureSession.capture(streamCaptureRequest, streamCaptureListener, null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i("Camera", "Surface is created");
        mStreamSurface = holder.getSurface();
        openCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i("Camera", "Surface has changed");
        openCamera();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void initPreviewRequest() {

        try {
            CaptureRequest.Builder requestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(mStreamSurface);
            CaptureRequest captureRequest = requestBuilder.build();
            CameraCaptureSession.CaptureCallback listener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                }
            };
            streamCaptureRequest = captureRequest;
            streamCaptureListener = listener;

        } catch (CameraAccessException e){
            Log.i("Camera", "Unable to access Camera");
            e.printStackTrace();
        }
    }

    public void initCaptureRequest(){
        try {
            CaptureRequest.Builder requestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(imgReader.getSurface());
            CaptureRequest captureRequest = requestBuilder.build();
            CameraCaptureSession.CaptureCallback listener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.i("Camera", "Capture Complete");
                }
            };
            stillCaptureRequest = captureRequest;
            stillCaptureListener = listener;
        } catch (CameraAccessException e){
            Log.i("Camera", "Unable to access Camera");
            e.printStackTrace();
        }
    }

    public void capture(View view){
        if (mCaptureSession == null) return;
        try {
            if (stillCaptureRequest == null || stillCaptureListener == null){
                initCaptureRequest();
            }
            mCaptureSession.capture(stillCaptureRequest, stillCaptureListener, null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (mCamera != null) {
            mCamera.close();
        }
        mCamera = null;
        mCameraManager = null;
        mCaptureSession = null;
        mStreamSurface = null;
        stillCaptureListener = null;
        stillCaptureRequest = null;
        streamCaptureRequest = null;
        streamSurface = null;
    }

    @Override
    public void onStart(){
        super.onStart();
        updateCurrentScreenRotation();
        initViews();
        initCamera();
        initModels();
    }

    @Override
    public void onPause(){
        super.onPause();
        if (mCamera != null) {
            mCamera.close();
        }
        mCamera = null;
        mCameraManager = null;
        mCaptureSession = null;
        mStreamSurface = null;
        stillCaptureListener = null;
        stillCaptureRequest = null;
        streamCaptureRequest = null;
        streamSurface = null;
    }

    public void onStop(){
        super.onStop();
        if (mCamera != null) {
            mCamera.close();
        }
        mCamera = null;
        mCameraManager = null;
        mCaptureSession = null;
        mStreamSurface = null;
        stillCaptureListener = null;
        stillCaptureRequest = null;
        streamCaptureRequest = null;
        streamSurface = null;
    }
}
