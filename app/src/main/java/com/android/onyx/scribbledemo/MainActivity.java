package com.android.onyx.scribbledemo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.android.onyx.scribbledemo.broadcast.GlobalDeviceReceiver;
import com.android.onyx.scribbledemo.databinding.ActivityMainBinding;
import com.android.onyx.scribbledemo.request.RendererToScreenRequest;
import com.android.onyx.utils.TouchUtils;
import com.onyx.android.sdk.OnyxSdk;
import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoFountainPen;
import com.onyx.android.sdk.pen.RawInputCallback;
import com.onyx.android.sdk.pen.TouchHelper;
import com.onyx.android.sdk.pen.data.TouchPointList;
import com.onyx.android.sdk.rx.RxManager;
import com.onyx.android.sdk.utils.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int RENDER_DEBOUNCE_INTERVAL = 500;

    private ActivityMainBinding binding;

    private GlobalDeviceReceiver deviceReceiver = new GlobalDeviceReceiver();
    private RxManager rxManager;

    private TouchHelper touchHelper;

    private Paint paint = new Paint();

    private Bitmap bitmap;
    private Canvas canvas;

    private final float STROKE_WIDTH = 3.0f;
    private ObservableEmitter<Object> emitter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        deviceReceiver.enable(this, true);
        binding.setModel(this);

        OnyxSdk.init(this);
        initPaint();
        initSurfaceView();
        initReceiver();
        initObservable();
    }

    @Override
    protected void onResume() {
        touchHelper.setRawDrawingEnabled(true);
        OnyxSdk.getInstance().setSideButtonSelection(true);
        super.onResume();
    }

    @Override
    protected void onPause() {
        touchHelper.setRawDrawingEnabled(false);
        OnyxSdk.getInstance().setSideButtonSelection(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        touchHelper.closeRawDrawing();
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        deviceReceiver.enable(this, false);
        super.onDestroy();
    }

    public RxManager getRxManager() {
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager();
        }
        return rxManager;
    }

    public void renderToScreen(SurfaceView surfaceView, Bitmap bitmap) {
        getRxManager().enqueue(new RendererToScreenRequest(surfaceView, bitmap), null);
    }

    private void initPaint() {
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    private void initSurfaceView() {
        touchHelper = TouchHelper.create(binding.surfaceview, callback);

        binding.surfaceview.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int
                    oldRight, int oldBottom) {
                if (cleanSurfaceView()) {
                    binding.surfaceview.removeOnLayoutChangeListener(this);
                }
                List<Rect> exclude = new ArrayList<>();
                exclude.add(getRelativeRect(binding.surfaceview, binding.buttonEraser));
                exclude.add(getRelativeRect(binding.surfaceview, binding.buttonPen));

                Rect limit = new Rect();
                binding.surfaceview.getLocalVisibleRect(limit);
                touchHelper.setStrokeWidth(STROKE_WIDTH)
                        .setLimitRect(limit, exclude)
                        .openRawDrawing();
                touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_BRUSH);
                binding.surfaceview.addOnLayoutChangeListener(this);
            }
        });

        final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                cleanSurfaceView();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                holder.removeCallback(this);
            }
        };
        binding.surfaceview.getHolder().addCallback(surfaceCallback);
    }

    private void initReceiver() {
        deviceReceiver.setSystemNotificationPanelChangeListener(new GlobalDeviceReceiver.SystemNotificationPanelChangeListener() {
            @Override
            public void onNotificationPanelChanged(boolean open) {
                touchHelper.setRawDrawingEnabled(!open);
                renderToScreen(binding.surfaceview, bitmap);
            }
        }).setSystemScreenOnListener(new GlobalDeviceReceiver.SystemScreenOnListener() {
            @Override
            public void onScreenOn() {
                renderToScreen(binding.surfaceview, bitmap);
            }
        });
    }

    private void initObservable() {
        ObservableOnSubscribe<Object> source = new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                MainActivity.this.emitter = emitter;
            }
        };
        Observable.create(source).debounce(RENDER_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS)
                .map(o -> {
                    renderToScreen(binding.surfaceview, bitmap);
                    return true;
                }).subscribe();
    }

    public void onPenClick() {
        touchHelper.setRawDrawingEnabled(true);
    }

    public void onEraserClick() {
        touchHelper.setRawDrawingEnabled(false);
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        cleanSurfaceView();
    }

    public Rect getRelativeRect(final View parentView, final View childView) {
        int[] parent = new int[2];
        int[] child = new int[2];
        parentView.getLocationOnScreen(parent);
        childView.getLocationOnScreen(child);
        Rect rect = new Rect();
        childView.getLocalVisibleRect(rect);
        rect.offset(child[0] - parent[0], child[1] - parent[1]);
        return rect;
    }

    private boolean cleanSurfaceView() {
        if (binding.surfaceview.getHolder() == null) {
            return false;
        }
        Canvas canvas = binding.surfaceview.getHolder().lockCanvas();
        if (canvas == null) {
            return false;
        }
        canvas.drawColor(Color.WHITE);
        binding.surfaceview.getHolder().unlockCanvasAndPost(canvas);
        return true;
    }

    private RawInputCallback callback = new RawInputCallback() {

        @Override
        public void onBeginRawDrawing(boolean b, TouchPoint touchPoint) {
            Log.d(TAG, "onBeginRawDrawing");
            Log.d(TAG, touchPoint.getX() + ", " + touchPoint.getY());
            TouchUtils.disableFingerTouch(getApplicationContext());
        }

        @Override
        public void onEndRawDrawing(boolean b, TouchPoint touchPoint) {
            Log.d(TAG, "onEndRawDrawing###");
            Log.d(TAG, touchPoint.getX() + ", " + touchPoint.getY());
            TouchUtils.enableFingerTouch(getApplicationContext());
        }

        @Override
        public void onRawDrawingTouchPointMoveReceived(TouchPoint touchPoint) {
            Log.d(TAG, "onRawDrawingTouchPointMoveReceived");
            Log.d(TAG, touchPoint.getX() + ", " + touchPoint.getY());
        }

        @Override
        public void onRawDrawingTouchPointListReceived(TouchPointList touchPointList) {
            Log.d(TAG, "onRawDrawingTouchPointListReceived");
            drawScribbleToBitmap(touchPointList.getPoints());
        }

        @Override
        public void onBeginRawErasing(boolean sideBtn, TouchPoint touchPoint) {
            Log.d(TAG, "onBeginRawErasing" + sideBtn);
        }

        @Override
        public void onEndRawErasing(boolean sideBtn, TouchPoint touchPoint) {
            Log.d(TAG, "onEndRawErasing");
            //Resume stroke style here
            touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_BRUSH);

            //Refresh view to hide dashed line
            emitRender(touchPoint);
        }

        @Override
        public void onRawErasingTouchPointMoveReceived(TouchPoint touchPoint) {
            Log.d(TAG, "onRawErasingTouchPointMoveReceived");
        }

        @Override
        public void onRawErasingTouchPointListReceived(TouchPointList touchPointList) {
            Log.d(TAG, "onRawErasingTouchPointListReceived");
        }
    };

    private void emitRender(TouchPoint touchPoint) {
        emitter.onNext(touchPoint);
    }

    private void drawScribbleToBitmap(List<TouchPoint> list) {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(binding.surfaceview.getWidth(), binding.surfaceview.getHeight(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }

        float maxPressure = EpdController.getMaxTouchPressure();
        NeoFountainPen.drawStroke(canvas, paint, list, NumberUtils.FLOAT_ONE, STROKE_WIDTH, maxPressure, false);
    }
}