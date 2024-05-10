package com.example.dxscanner.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.example.dxscanner.R;
import com.example.dxscanner.processing.ImageProcessingUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {
    private static  final  int REQUEST_CAMERA_PERMISSION_RESULT=0;
    private static  final  int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT=1;

    private static final int STATE_PREVIEW=0;
    private static final int STATE_WAIT_LOCK=1;
    private int mCaptureState=STATE_PREVIEW;
    private static boolean FRONT_FACING=false;
    private static boolean FLASH=false;
    private static boolean CAPTURE_TIMER=false;

    TextureView mTextureView;
    CircleImageView gallery;
    CircleImageView switchCamera;
    ImageButton cameraButton;
    ImageView flash;
    ImageView settings;
    ImageView timer;
    ImageView filters;
    Handler timerHandler;
    Runnable timerRunnable;
    MediaActionSound mediaActionSound; // for shutter sound

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            setUpCamera(i,i1);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };
    private CameraDevice mCameraDevice;
    private final CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice=cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice=null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice=null;
        }
    };
    private String mCameraId;
    private int timeDelay=0;
    private Size mPreviewSize;
    private Size mImageSize;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //when the image is captured properly then this method will run
                    mBackgroundHandler.post(new ImageSaver(imageReader.acquireLatestImage()));
                }
            };
    private class ImageSaver implements  Runnable{
        private final Image mImage;
        public ImageSaver(Image image) {
            mImage=image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            FileOutputStream fileOutputStream=null;
            try {
                fileOutputStream = new FileOutputStream(mImageFileName);
//                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
//                Matrix matrix = new Matrix();
//                matrix.postRotate(90);
//                // Rotate the Bitmap if necessary
//                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//                // Compress the rotated Bitmap as WebP
//                rotatedBitmap= ImageProcessingUtils.getProcessedBitmapWithWhiteBackground(rotatedBitmap);
//                rotatedBitmap.compress(Bitmap.CompressFormat.WEBP, 100, fileOutputStream);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                mImage.close();
                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mImageFileName)));
                sendBroadcast(mediaStoreUpdateIntent);
                if(fileOutputStream!=null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private int mTotalRotation;
    private CameraCharacteristics xCameraCharacteristics; //on tap focus
//    private boolean mManualFocusEngaged=false; //on tap focus
    private CameraCaptureSession mPreviewCaptureSession;
    private final CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult captureResult){
            switch (mCaptureState){
                case STATE_PREVIEW:
                    // do nothing
                    break;
                case STATE_WAIT_LOCK:
                    mCaptureState=STATE_PREVIEW;
                    Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                        Toast.makeText(MainActivity.this, "AF LOCKED", Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(MainActivity.this, "AF NOT LOCKED", Toast.LENGTH_SHORT).show();
                    }
                    startStillCaptureRequest();
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }
    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size size, Size t1) {
            return Long.signum((long) size.getWidth()*size.getHeight() /
                    (long) t1.getWidth()*t1.getHeight());
        }
    }
    private File mImageFolder;
    private String mImageFileName;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createImageFolder();
        init();
        cameraButton.setOnClickListener(v->{
            checkWriteStoragePermission();
            if(CAPTURE_TIMER){
                CAPTURE_TIMER=false;
                cameraButton.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.click_btn));
                timerHandler.removeCallbacks(timerRunnable);
            }else {
                lockFocus();
            }
        });
        switchCamera.setOnClickListener(v->{
            FRONT_FACING= !FRONT_FACING;
            if(mCameraDevice!=null) mCameraDevice.close();
            mCameraDevice=null;
            if(mTextureView.isAvailable()){
                setUpCamera(mTextureView.getWidth(),mTextureView.getHeight());
                connectCamera();
            }else{
                mTextureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        });
        flash.setOnClickListener(v->{
            if(FLASH) {
                FLASH=false;
                flash.setImageResource(R.drawable.flash_off);
            }else{
                FLASH=true;
                flash.setImageResource(R.drawable.flash_on);
            }
        });
        gallery.setOnClickListener(v->{
            startActivity(new Intent(this, ImageGallery.class));
        });
        timer.setOnClickListener(v->{showPOPUPWindow(R.layout.popup_window_timer,timer);});
    }
    private void init(){
        mTextureView = findViewById(R.id.textureView);
        gallery = findViewById(R.id.gallery) ;
        switchCamera = findViewById(R.id.switchCamera) ;
        cameraButton = findViewById(R.id.cameraButton) ;
        flash=findViewById(R.id.flash);
        settings=findViewById(R.id.settings);
        timer=findViewById(R.id.timer);
        filters=findViewById(R.id.filters);
        timerHandler=new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                cameraButton.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.click_btn));
                capture();
                CAPTURE_TIMER=false;
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediaActionSound = new MediaActionSound();
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
        startBackgroundThread();
        if(mTextureView.isAvailable()){
            setUpCamera(mTextureView.getWidth(),mTextureView.getHeight());
            connectCamera();
        }else{
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQUEST_CAMERA_PERMISSION_RESULT){
            if(grantResults[0]!= PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "App will not work without camera services", Toast.LENGTH_SHORT).show();
            }
        }

        else if(requestCode==REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Permission successfully granted", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this, "App needs to save image to run", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        timerHandler.removeCallbacks(timerRunnable);
        if(mCameraDevice!=null) mCameraDevice.close();
        mCameraDevice=null;
        stopBackgroundThread();
        mediaActionSound.release();
        super.onPause();
    }

    private void setUpCamera(int width, int height){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cid:cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cid);
                xCameraCharacteristics=cameraCharacteristics; // on tap focus
                if(FRONT_FACING){
                    if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK) continue;
                    //getting the resolutions of the camera
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
                    mTotalRotation = sensorToDeviceOrientation(cameraCharacteristics,deviceRotation);
                    boolean swapRotation = mTotalRotation==90|| mTotalRotation==270;
                    int rotatedWidth=width;
                    int rotatedHeight=height;
                    if(swapRotation){
                        rotatedHeight=width;
                        rotatedWidth =height;
                    }
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth,rotatedHeight);
                    mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG),rotatedWidth,rotatedHeight);
                }else{
                    if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT) continue;
                    //getting the resolutions of the camera
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
                    mTotalRotation = sensorToDeviceOrientation(cameraCharacteristics,deviceRotation);
                    boolean swapRotation = mTotalRotation==90|| mTotalRotation==270;
                    int rotatedWidth=width;
                    int rotatedHeight=height;
                    if(swapRotation){
                        rotatedHeight=width;
                        rotatedWidth =height;
                    }
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth,rotatedHeight);
                    mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG),rotatedWidth,rotatedHeight);
                }
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(),mImageSize.getHeight(),ImageFormat.JPEG,1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
                mCameraId=cid;
                return;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED){
                cameraManager.openCamera(mCameraId,mCameraDeviceStateCallback,mBackgroundHandler);
            }else{
                if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA))
                    Toast.makeText(this, "Video App needs camera access", Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{android.Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_RESULT);
            }
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void startPreview(){
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //below new line
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE,CaptureRequest.CONTROL_EFFECT_MODE_SEPIA);

            mCaptureRequestBuilder.addTarget(mSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            mPreviewCaptureSession=cameraCaptureSession;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null,mBackgroundHandler);
                            }catch (CameraAccessException e){
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Unable to show the camera preview", Toast.LENGTH_SHORT).show();
                        }
                    },null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void startStillCaptureRequest(){
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,mTotalRotation);

            CameraCaptureSession.CaptureCallback mStillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            try {
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };

            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mStillCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * free the camera resource
     */
    private void closeCamera(){
        if(mCameraDevice!=null) mCameraDevice.close();
        mCameraDevice=null;
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread("DxScanner");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread=null;
            mBackgroundHandler=null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static int sensorToDeviceOrientation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation=ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation+deviceOrientation+360)%360;
    }
    // choices consist of an array of camera resolutions

    private static Size chooseOptimalSize(Size[] choices, int width, int height){
        List<Size> bigEnough = new ArrayList<>();
        for(Size option:choices){
            if(option.getHeight()==option.getWidth() * height/width
                    && option.getWidth()>=width && option.getHeight()>=height) bigEnough.add(option);
        }
        if(bigEnough.size()>0) return Collections.min(bigEnough,new CompareSizeByArea());
        else return choices[0];
    }

    private void createImageFolder(){
        File imageFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFolder,"DxScanner");
        if(!mImageFolder.exists()) mImageFolder.mkdirs();
    }

    private File createImageFileName() throws IOException{
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",new Locale("en","GB")).format(new Date());
        String prepend = "IMAGE_"+timeStamp+"_";
        File imageFile = File.createTempFile(prepend,".jpg",mImageFolder);
        mImageFileName = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkWriteStoragePermission(){
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.Q){
            Toast.makeText(this, "Write External Storage permission granted", Toast.LENGTH_SHORT).show();
            return ;
        }
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==
                PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, "Write External Storage permission granted", Toast.LENGTH_SHORT).show();
        }else{
            if(shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                Toast.makeText(this, "apps needs to be able to save videos", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
        }
    }

    private void lockFocus(){
        mCaptureState=STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);//setting up the focus
        if(FLASH) mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_SINGLE);
        else mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);

        if(timeDelay==0){
            CAPTURE_TIMER=false;
            capture();
        }else{
            CAPTURE_TIMER=true;
            cameraButton.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.stop_icon));
            timerHandler.postDelayed(timerRunnable,timeDelay*1000L);
        }

    }

    private void capture(){
        try {
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(),mPreviewCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void showPOPUPWindow(int layout, View timerView){
        PopupWindow popupWindow = new PopupWindow(this);
        View view=getLayoutInflater().inflate(layout,null);
        popupWindow.setContentView(view);
        ImageView timerOff=view.findViewById(R.id.timerOffIV);
        ImageView timer2S=view.findViewById(R.id.timer2sIV);
        ImageView timer5S=view.findViewById(R.id.timer5sIV);
        ImageView timer7S=view.findViewById(R.id.timer7sIV);
        ImageView timer9S=view.findViewById(R.id.timer9sIV);
        timer2S.setOnClickListener(v->{
            timeDelay=2;
            timer.setImageResource(R.drawable.timer2s);
            popupWindow.dismiss();
        });
        timer5S.setOnClickListener(v->{
            timeDelay=5;
            timer.setImageResource(R.drawable.timer5s);
            popupWindow.dismiss();
        });
        timer7S.setOnClickListener(v->{
            timeDelay=7;
            timer.setImageResource(R.drawable.timer7s);
            popupWindow.dismiss();
        });
        timer9S.setOnClickListener(v->{
            timeDelay=9;
            timer.setImageResource(R.drawable.timer9s);
            popupWindow.dismiss();
        });
        timerOff.setOnClickListener(v->{
            timeDelay=0;
            timer.setImageResource(R.drawable.timer_off);
            popupWindow.dismiss();
        });
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(getDrawable(R.drawable.timer_popupwindow_background));
        popupWindow.showAsDropDown(timerView);
    }






// on tap focus -working part

//    @Override
//    public boolean onTouchEvent(MotionEvent motionEvent) {
//        final int actionMasked = motionEvent.getActionMasked();
//        if (actionMasked != MotionEvent.ACTION_DOWN) {
//            return false;
//        }
//        if (mManualFocusEngaged) {
//            Log.d("TAG", "Manual focus already engaged");
//            return true;
//        }
//
//        final Rect sensorArraySize = xCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
//
//        final int y = (int)((motionEvent.getX() / (float)mPreviewSize.getWidth())  * (float)sensorArraySize.height());
//        final int x = (int)((motionEvent.getY() / (float)mPreviewSize.getHeight()) * (float)sensorArraySize.width());
//        final int halfTouchWidth  = 150; //(int)motionEvent.getTouchMajor(); //TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
//        final int halfTouchHeight = 150; //(int)motionEvent.getTouchMinor();
//        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth,  0),
//                Math.max(y - halfTouchHeight, 0),
//                halfTouchWidth  * 2,
//                halfTouchHeight * 2,
//                MeteringRectangle.METERING_WEIGHT_MAX - 1);
//
//        CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
//            @Override
//            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//                super.onCaptureCompleted(session, request, result);
//                mManualFocusEngaged = false;
//
//                if (request.getTag() == "FOCUS_TAG") {
//                    //the focus trigger is complete -
//                    //resume repeating (preview surface will get frames), clear AF trigger
//                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
//                    try {
//                        mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
//                    } catch (CameraAccessException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//            }
//
//            @Override
//            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
//                super.onCaptureFailed(session, request, failure);
//                Log.e("TAG", "Manual AF failure: " + failure);
//                mManualFocusEngaged = false;
//            }
//        };
//
//        //first stop the existing repeating request
//        try {
//            mPreviewCaptureSession.stopRepeating();
//        } catch (CameraAccessException e) {
//            throw new RuntimeException(e);
//        }
//
//        //cancel any existing AF trigger (repeated touches, etc.)
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
//        try {
//            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            throw new RuntimeException(e);
//        }
//
//        //Now add a new AF trigger with focus region
//        if (isMeteringAreaAFSupported()) {
//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
//        }
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//        mCaptureRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview
//
//        //then we ask for a single request (not repeating!)
//        try {
//            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            throw new RuntimeException(e);
//        }
//        mManualFocusEngaged = true;
//        return super.onTouchEvent(motionEvent);
//    }
//
//    private boolean isMeteringAreaAFSupported() {
//        return xCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1;
//    }


}