package com.youlong.hd;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;


public class ScreenFilterService extends Service {

    private static final String CHANNEL_ID = "screen_filter_channel";
    private static final int NOTIFICATION_ID = 2001;
    public static final String ACTION_STOP = "com.youlong.hd.action.STOP_FILTER";

    private static WindowManager windowManager;
    private static OverlayView overlayView;
    private static volatile boolean running = false;

    private static volatile int sPreset = 0;
    private static volatile float sBrightness   = 100f;
    private static volatile float sContrast     = 100f;
    private static volatile float sSaturation   = 100f;
    private static volatile float sTemperature  = 50f;

    // ============================================================
    
    // ============================================================

    
    private static final float[] MATRIX_STANDARD = {
            1.05f, 0f,    0f,    0f, 8f,   // R
            0f,    1.05f, 0f,    0f, 8f,   // G
            0f,    0f,    1.04f, 0f, 6f,   // B
            0f,    0f,    0f,    1f, 0f    // A
    };

    
    private static final float[] MATRIX_GAME = {
            1.06f, 0f,    0f,    0f, 5f,   // R
            0f,    1.05f, 0f,    0f, 5f,   // G
            0f,    0f,    1.07f, 0f, 4f,   // B
            0f,    0f,    0f,    1f, 0f    // A
    };

    // ============================================================
    
    // ============================================================

    private static class OverlayView extends View {
        private final ColorMatrix mColorMatrix = new ColorMatrix();
        private final Paint mFilterPaint = new Paint();
        private float mAlpha = 0f;     // 0~255
        private int mPreset = 0;       

        OverlayView(Context context) {
            super(context);
            setFocusable(false);
            setFocusableInTouchMode(false);
            setClickable(false);
            setEnabled(false);
            
            mFilterPaint.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
            setLayerType(LAYER_TYPE_HARDWARE, mFilterPaint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mAlpha < 2f) return;
            int a = Math.round(mAlpha);
            a = Math.max(0, Math.min(255, a));
            
            
            int baseColor = Color.argb(a, 245, 245, 245);
            canvas.drawColor(baseColor);
        }

        
        void rebuildFilter(int preset, float[] presetMatrix,
                           float brightness, float contrast,
                           float saturation, float temperature) {
            float br = (brightness - 100f) / 100f;   // -1 ~ +1
            float co = (contrast - 100f) / 100f;
            float sa = (saturation - 100f) / 100f;
            float te = (temperature - 50f) / 50f;

            
            mPreset = preset;

            
            float[] m = presetMatrix.clone();

            
            m[4]  += br * 10f;
            m[9]  += br * 10f;
            m[14] += br * 8f;

            
            m[4]  += te * 16f;
            m[14] -= te * 16f;

            
            

            mColorMatrix.set(m);
            mFilterPaint.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
            setLayerType(LAYER_TYPE_HARDWARE, mFilterPaint);

            
            
            float dev = Math.abs(br) * 0.5f + Math.abs(co) * 0.25f
                      + Math.abs(sa) * 0.25f + Math.abs(te) * 0.5f;
            mAlpha = 6f + dev * 10f;    
            mAlpha = Math.max(0, Math.min(20, mAlpha));

            invalidate();
        }
    }

    // ============================================================
    
    // ============================================================

    private static float[] getPresetMatrix() {
        return sPreset == 1 ? MATRIX_GAME : MATRIX_STANDARD;
    }

    // ============================================================
    
    // ============================================================

    private static void applyFilter() {
        if (overlayView == null) return;
        overlayView.rebuildFilter(
                sPreset,
                getPresetMatrix(),
                sBrightness, sContrast, sSaturation, sTemperature
        );
    }

    // ============================================================
    
    // ============================================================

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        addOverlay();
        running = true;
    }

    private void addOverlay() {
        if (overlayView != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new OverlayView(this);

        applyFilter();

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    
    // ============================================================

    public static void setPreset(int p) {
        sPreset = p == 1 ? 1 : 0;
        applyFilter();
    }

    public static int getPreset() { return sPreset; }

    public static String getPresetName() {
        return sPreset == 1 ? "冷色高清款" : "标准清晰款";
    }

    public static void setFilter(float b, float c, float s, float t) {
        sBrightness  = clamp(b, 0, 200);
        sContrast    = clamp(c, 0, 200);
        sSaturation  = clamp(s, 0, 200);
        sTemperature = clamp(t, 0, 100);
        postUpdate();
    }

    public static void setBrightness(float v)  { sBrightness  = clamp(v, 0, 200); postUpdate(); }
    public static void setContrast(float v)    { sContrast    = clamp(v, 0, 200); postUpdate(); }
    public static void setSaturation(float v)  { sSaturation  = clamp(v, 0, 200); postUpdate(); }
    public static void setTemperature(float v) { sTemperature = clamp(v, 0, 100); postUpdate(); }

    public static float getBrightness()   { return sBrightness; }
    public static float getContrast()     { return sContrast; }
    public static float getSaturation()   { return sSaturation; }
    public static float getTemperature()  { return sTemperature; }
    public static boolean isRunning()     { return running; }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, ScreenFilterService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    // ============================================================
    
    // ============================================================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        overlayView = null;
        windowManager = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ============================================================
    
    // ============================================================

    private static void postUpdate() {
        OverlayView v = overlayView;
        if (v != null) {
            v.post(() -> {
                
                if (overlayView != null) {
                    applyFilter();
                }
            });
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ============================================================
    
    // ============================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "屏幕滤镜", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("屏幕滤镜运行中，点击通知可停止");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent stopI = new Intent(this, ScreenFilterService.class);
        stopI.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopI, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("屏幕滤镜运行中")
                .setContentText(getPresetName() + " · 亮度/对比度/饱和度/冷暖色")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(contentPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止滤镜", stopPi)
                .build();
    }
}