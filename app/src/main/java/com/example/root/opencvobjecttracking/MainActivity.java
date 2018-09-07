package com.example.root.opencvobjecttracking;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.params.BlackLevelPattern;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.root.cameramodule.CameraApi;
import com.example.root.cameramodule.ICameraApiCallback;

import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect2d;
import org.opencv.tracking.Tracker;
import org.opencv.tracking.TrackerKCF;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, ICameraApiCallback
        , View.OnClickListener, ResizeRectangleView.CropVisionFinishCallback {
    private static final String TAG = "MainActivity";
    private final int PERMISSION_CAMERA_REQUEST_CODE = 0x10;
    private boolean isCheckPermissionOk = false;
    private boolean isLoadSuccess = false;
    private volatile boolean isStartTracking = false;
    private volatile boolean isSelectArea = false;
    private boolean isInit = false;

    private SurfaceView mSVContent;
    private ResizeRectangleView mResizeRectView;
    private Button mResetView;
    private Button mChooseTrackerView;
    private ViewGroup.LayoutParams layoutParams;
    private SurfaceHolder mSurfaceHolder;
    private float ratio;
    private int mSurfaceViewWidth;
    private int mSurfaceViewHeight;
    public static int previewWidth = 1280;
    public static int previewHeight = 720;

    private int mCurrentCameraIndex = CameraApi.CAMERA_INDEX_BACK;

    private CameraRawData mCameraRawData;
    private final int BUFFER_SIZE = 5;
    private BlockingQueue<CameraRawData> mFreeQueue = new LinkedBlockingQueue<>(BUFFER_SIZE);
    private BlockingQueue<CameraRawData> mFrameDataQueue = new LinkedBlockingQueue<>(BUFFER_SIZE);

    private Mat mSrcMat;
    /**
     * for front camera need flip data
     */
    private Mat mDesMat = null;
    private Tracker mTracker;
    private Rect2d rect2d;

    private Thread mTrackingThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSVContent = findViewById(R.id.sv_content);
        mSVContent.getHolder().addCallback(this);
        mResizeRectView = findViewById(R.id.resize_rect);
        mResetView = findViewById(R.id.reset_track);
        mChooseTrackerView = findViewById(R.id.choose_tracker);
        mResetView.setOnClickListener(this);
        mChooseTrackerView.setOnClickListener(this);
        mResizeRectView.setmUpCallback(this);

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
        } else {
            isCheckPermissionOk = true;
        }

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        mSurfaceViewWidth = size.x;
        mSurfaceViewHeight = size.y;
        Log.e(TAG, "onCreate: " + size.x + "--" + size.y);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.e(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reset();
        mSrcMat.release();
        mSrcMat = null;
        if (mDesMat != null) {
            mDesMat.release();
            mDesMat = null;
        }
    }

    private void init() {
        mSrcMat = new Mat(previewHeight, previewWidth, CvType.CV_8UC1);
        if (mCurrentCameraIndex == CameraApi.CAMERA_INDEX_FRONT) {
            mDesMat = new Mat(previewHeight, previewWidth, CvType.CV_8UC1);
        }
        mTracker = TrackerKCF.create();
        rect2d = new Rect2d();

        if (mFreeQueue.isEmpty()) {
            for (int i = 0; i < BUFFER_SIZE; i++) {
                CameraRawData rawData = new CameraRawData();
                mFreeQueue.offer(rawData);
            }
        }
    }

    private LoaderCallbackInterface mLoaderCallback = new LoaderCallbackInterface() {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                isLoadSuccess = true;
                init();
            }
        }

        @Override
        public void onPackageInstall(int operation, InstallCallbackInterface callback) {

        }
    };


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (isCheckPermissionOk) {
            CameraApi.getInstance().setCameraId(mCurrentCameraIndex);
            CameraApi.getInstance().initCamera(this, this);
            CameraApi.getInstance().setPreviewSize(new Size(previewWidth, previewHeight));
            CameraApi.getInstance().setFps(30).configCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceHolder = holder;
        ratio = (float) (previewWidth) / (float) (previewHeight);
        layoutParams = mSVContent.getLayoutParams();
        if (mSurfaceViewHeight > mSurfaceViewWidth) {
            //竖屏
            mSurfaceViewHeight = (int) (mSurfaceViewWidth * ratio);
        } else {
            //横屏
            mSurfaceViewWidth = (int) (mSurfaceViewHeight * ratio);
        }

        Log.e(TAG, "surfaceChanged:mSurfaceViewWidth= " + mSurfaceViewWidth);
        Log.e(TAG, "surfaceChanged:mSurfaceViewHeight= " + mSurfaceViewHeight);
        layoutParams.width = mSurfaceViewWidth;
        layoutParams.height = mSurfaceViewHeight;
        mSVContent.setLayoutParams(layoutParams);
        if (isCheckPermissionOk) {
            CameraApi.getInstance().startPreview(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.reset_track:
                reset();
                mResizeRectView.onClearCanvas();
                break;
            case R.id.choose_tracker:
                Toast.makeText(this, "choose tracker click", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    private void reset() {
        isStartTracking = false;
        isSelectArea = false;
        if (mTrackingThread != null) {
            try {
                mTrackingThread.join();
                mTrackingThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mTracker = null;
        isInit = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mResizeRectView.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isCheckPermissionOk = true;
                onStartPreview();
            }
        }
    }

    private void onStartPreview() {
        CameraApi.getInstance().setCameraId(mCurrentCameraIndex);
        CameraApi.getInstance().initCamera(this, this);
        CameraApi.getInstance().setPreviewSize(new Size(previewWidth, previewHeight));
        CameraApi.getInstance().setFps(30).configCamera();
        CameraApi.getInstance().startPreview(mSurfaceHolder);
    }

    @Override
    public void onPreviewFrameCallback(byte[] data, Camera camera) {
        camera.addCallbackBuffer(data);
        if (isStartTracking) {
            CameraRawData rawData = mFreeQueue.poll();
            if (rawData != null) {
                rawData.setRawData(data);
                rawData.setTimestamp(System.currentTimeMillis());
                mFrameDataQueue.offer(rawData);
            }
        }
    }

    @Override
    public void onNotSupportErrorTip(String message) {

    }

    @Override
    public void onCameraInit(Camera camera) {

    }

    @Override
    public void onCropVisionAreaFinish(Rect rect) {
        isStartTracking = true;
        isSelectArea = true;
        rect2d.x = rect.left;
        rect2d.y = rect.top;
        rect2d.width = Math.abs(rect.right - rect.left);
        rect2d.height = Math.abs(rect.bottom - rect.top);

        if (mTrackingThread == null) {
            mTrackingThread = new ObjectTrackingThread("obj-tracking-thread");
            mTrackingThread.start();
        }
        if (mTracker == null) {
            mTracker = TrackerKCF.create();
        }
    }


    /**
     * tracking thread
     */
    boolean isUpdate;

    class ObjectTrackingThread extends Thread {
        public ObjectTrackingThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            super.run();
            while (isStartTracking && isLoadSuccess) {
                try {
                    mCameraRawData = mFrameDataQueue.poll(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mCameraRawData == null) {
                    continue;
                }
                byte[] data = mCameraRawData.getRawData();
                mSrcMat.put(0, 0, data);
                if (mDesMat != null) {
                    Core.flip(mSrcMat, mDesMat, 1);
                    if (!isInit) {
                        isInit = mTracker.init(mDesMat, rect2d);
                    }
                    isUpdate = mTracker.update(mDesMat, rect2d);
                } else {
                    if (!isInit) {
                        isInit = mTracker.init(mSrcMat, rect2d);
                    }
                    isUpdate = mTracker.update(mSrcMat, rect2d);
                }
                mResizeRectView.setTrackResult(isUpdate);
                if (isUpdate) {

                    mResizeRectView.onShowTracking(rect2d);
                }
//                Log.e(TAG, "run: 2222---isInit = " + isInit + "---isUpdate = " + isUpdate);
//                Log.e(TAG, "run: 333---" + rect2d.x + "--" + rect2d.y);
                mFreeQueue.offer(mCameraRawData);
            }
        }
    }
}
