package com.example.root.opencvobjecttracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.opencv.core.Rect2d;

public class ResizeRectangleView extends View {
    private final int DIRECTION_RIGHT_DOWN = 0x11;
    private final int DIRECTION_RIGHT_UP = 0x12;
    private final int DIRECTION_LEFT_DOWN = 0x13;
    private final int DIRECTION_LEFT_UP = 0x14;

    private int mCurrentDirection = DIRECTION_RIGHT_DOWN;

    private PorterDuffXfermode mClearPorter;
    private PorterDuffXfermode mSrcPorter;

    private Point mPoint0;
    private Point mPoint1;
    private Point mPoint2;
    private Point mPoint3;

    private Paint mRectanglePaint;

    private Paint mRectLoseTipPaint;
    private Paint mClearPaint;


    private CropVisionFinishCallback mUpCallback;

    private boolean isUp = false;
    private boolean isClean = false;
    private boolean isCropOk = false;
    private boolean isUpdate = false;
    private int mCanvasWidth;
    private int mCanvasHeight;

    private static final String TAG = "ResizeRectangleView";

    /**
     *
     *
     */

    public ResizeRectangleView(Context context) {
        this(context, null);
    }

    public ResizeRectangleView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ResizeRectangleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setmUpCallback(CropVisionFinishCallback callback) {
        mUpCallback = callback;
    }

    private void init(Context context) {
//        setFocusable(true);
        mPoint0 = new Point();
        mPoint1 = new Point();
        mPoint2 = new Point();
        mPoint3 = new Point();

        mRectanglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRectanglePaint.setStyle(Paint.Style.STROKE);
        mRectanglePaint.setARGB(0xFF, 0, 0xFF, 0);
        mRectanglePaint.setStrokeWidth(context.getResources().getDimension(R.dimen.resize_rect_bound_width));

        mClearPaint = new Paint();
        mClearPorter = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
        mSrcPorter = new PorterDuffXfermode(PorterDuff.Mode.SRC);

        mRectLoseTipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRectLoseTipPaint.setStyle(Paint.Style.STROKE);
        mRectLoseTipPaint.setARGB(0xFF, 0xFF, 0, 0);
        mRectLoseTipPaint.setStrokeWidth(context.getResources().getDimension(R.dimen.resize_rect_bound_width));
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mCanvasWidth = canvas.getWidth();
        mCanvasHeight = canvas.getHeight();
        if (isClean) {
            isUp = false;
            mClearPaint.setXfermode(mClearPorter);
            canvas.drawPaint(mClearPaint);
            mClearPaint.setXfermode(mSrcPorter);
        } else {
            if (!isUp){
                canvas.drawRect(mPoint0.x, mPoint0.y, mPoint2.x, mPoint2.y, mRectanglePaint);
            }else {
                if (isUpdate){
                    canvas.drawRect(mPoint0.x, mPoint0.y, mPoint2.x, mPoint2.y, mRectanglePaint);
                }else {
                    canvas.drawRect(mPoint0.x, mPoint0.y, mPoint2.x, mPoint2.y, mRectLoseTipPaint);
                }
            }

        }

    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    int startX;
    int startY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                int x0 = (int) event.getX();
                int y0 = (int) event.getY();
                startX = x0;
                startY = y0;
                init4Point(startX, startY);
                isClean = false;
                Log.e(TAG, "onTouchEvent: action down");
                break;
            case MotionEvent.ACTION_MOVE:
                int x1 = (int) event.getX();
                int y1 = (int) event.getY();
                mPoint1.x = x1;
                mPoint2.x = x1;
                mPoint2.y = y1;
                mPoint3.y = y1;
                if (x1 - startX > 0) {
                    if (y1 - startY > 0) {
                        mCurrentDirection = DIRECTION_RIGHT_DOWN;
                    } else {
                        mCurrentDirection = DIRECTION_RIGHT_UP;
                    }

                } else {
                    if (y1 - startY > 0) {
                        mCurrentDirection = DIRECTION_LEFT_DOWN;
                    } else {
                        mCurrentDirection = DIRECTION_LEFT_UP;
                    }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                isUp = true;
                int upX = (int) event.getX();
                int upY = (int) event.getY();
                Log.e(TAG, "onTouchEvent: upX=" + upX + "--upY=" + upY);
                int left, top, right, bottom;
                switch (mCurrentDirection) {
                    case DIRECTION_RIGHT_DOWN:
                        left = mPoint0.x;
                        top = mPoint0.y;
                        right = mPoint2.x;
                        bottom = mPoint2.y;
                        break;
                    case DIRECTION_RIGHT_UP:
                        left = mPoint0.x;
                        top = mPoint2.y;
                        right = mPoint2.x;
                        bottom = mPoint0.y;
                        break;
                    case DIRECTION_LEFT_DOWN:
                        left = mPoint2.x;
                        top = mPoint0.y;
                        right = mPoint0.x;
                        bottom = mPoint2.y;
                        break;
                    case DIRECTION_LEFT_UP:
                        left = mPoint2.x;
                        top = mPoint2.y;
                        right = mPoint0.x;
                        bottom = mPoint0.y;
                        break;
                    default:
                        left = mPoint0.x;
                        top = mPoint0.y;
                        right = mPoint2.x;
                        bottom = mPoint2.y;
                        break;
                }
                if (mUpCallback != null) {

                    if (mPoint2.x > mCanvasWidth) {
                        mPoint2.x = mCanvasWidth;
                    }

                    int scaleLeft = (int) ((float) left / getWidthScale(MainActivity.previewWidth));
                    int scaleRight = (int) ((float) right / getWidthScale(MainActivity.previewWidth));
                    int scaleTop = (int) ((float) top / getHeightScale(MainActivity.previewHeight));
                    int scaleBottom = (int) ((float) bottom / getHeightScale(MainActivity.previewHeight));
                    isCropOk = true;
                    mUpCallback.onCropVisionAreaFinish(new Rect(scaleLeft, scaleTop, scaleRight, scaleBottom));
                }
                invalidate();
                break;
            default:
                break;
        }
        invalidate();
        return super.onTouchEvent(event);
    }

    public void onClearCanvas() {
        isClean = true;
        invalidate();
    }

    private void init4Point(int x, int y) {
        mPoint1.x = x;
        mPoint2.x = x;
        mPoint3.x = x;
        mPoint0.x = x;

        mPoint0.y = y;
        mPoint1.y = y;
        mPoint2.y = y;
        mPoint3.y = y;
    }

    public void onShowTracking(Rect2d rect2d) {
        mPoint0.x = (int) (rect2d.x * getWidthScale(MainActivity.previewWidth));
        mPoint0.y = (int) (rect2d.y * getHeightScale(MainActivity.previewHeight));

        mPoint2.x = (int) ((rect2d.x+rect2d.width)*getWidthScale(MainActivity.previewWidth));
        mPoint2.y = (int) ((rect2d.y+rect2d.height)*getHeightScale(MainActivity.previewHeight));
        postInvalidate();
    }

    public void setTrackResult(boolean isUpdate) {
        this.isUpdate = isUpdate;
        postInvalidate();
    }

    /**
     * Canvas Coordinate and Preview data Coordinate translate
     *
     * @param w
     * @return
     */
    public float getWidthScale(int w) {
        return (float) mCanvasWidth / (float) w;
    }

    /**
     * Canvas Coordinate and Preview data Coordinate translate
     *
     * @param h
     * @return
     */
    public float getHeightScale(int h) {
        return (float) mCanvasHeight / (float) h;
    }

    /**
     * 获取所划区域对应的Rect
     */
    public interface CropVisionFinishCallback {
        void onCropVisionAreaFinish(Rect rect);
    }
}
