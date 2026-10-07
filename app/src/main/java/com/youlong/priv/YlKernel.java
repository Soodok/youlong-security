package com.youlong.priv;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.youlong.hd.YouLongApp;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public final class YlKernel {

    private static final String TAG = "YlKernel";

    
    private static final long READY_TIMEOUT_MS = 15_000L;

    private static volatile YlKernel sInstance;

    private final Handler mMain = new Handler(Looper.getMainLooper());

    private volatile YlService.Proxy mService;
    private volatile IBinder mServiceBinder;
    private volatile boolean mStarted;

    
    private final AtomicReference<CountDownLatch> mPendingLatch = new AtomicReference<>();

    private final CopyOnWriteArrayList<BinderReceivedListener> mReceivedListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BinderDeadListener> mDeadListeners =
            new CopyOnWriteArrayList<>();

    
    public interface BinderReceivedListener {
        void onBinderReceived();
    }

    
    public interface BinderDeadListener {
        void onBinderDead();
    }

    private YlKernel() {}

    public static YlKernel get() {
        YlKernel k = sInstance;
        if (k == null) {
            synchronized (YlKernel.class) {
                if (sInstance == null) sInstance = new YlKernel();
                k = sInstance;
            }
        }
        return k;
    }

    // ==================================================================
    
    // ==================================================================

    public boolean pingBinder() {
        YlService.Proxy s = mService;
        return s != null && s.ping();
    }

    public boolean checkSelfPermission(String permission) {
        YlService.Proxy s = mService;
        return s != null && s.checkPermission(permission);
    }

    
    public Process newProcess(String[] cmd, String[] env, String dir) throws Exception {
        YlService.Proxy s = ensureService();
        if (s == null) throw new IllegalStateException("特权服务未就绪");
        return s.newProcess(cmd, env, dir);
    }

    public int getServerUid() {
        YlService.Proxy s = mService;
        return s == null ? -1 : s.getServerUid();
    }

    public void requestPermission(int requestCode, String permission) {
        YlService.Proxy s = mService;
        if (s != null) s.requestPermission(requestCode, permission);
    }

    public void addBinderReceivedListener(BinderReceivedListener l) {
        if (l == null) return;
        mReceivedListeners.add(l);
        if (pingBinder()) {
            mMain.post(l::onBinderReceived);
        }
    }

    public void removeBinderReceivedListener(BinderReceivedListener l) {
        if (l != null) mReceivedListeners.remove(l);
    }

    public void addBinderDeadListener(BinderDeadListener l) {
        if (l != null) mDeadListeners.add(l);
    }

    public void removeBinderDeadListener(BinderDeadListener l) {
        if (l != null) mDeadListeners.remove(l);
    }

    // ==================================================================
    
    // ==================================================================

    
    public YlService.Proxy ensureService() {
        YlService.Proxy s = mService;
        if (s != null && s.ping()) return s;

        Context ctx = appContext();
        if (ctx == null) {
            Log.w(TAG, "拿不到 Context，无法拉起服务端");
            return null;
        }
        synchronized (this) {
            s = mService;
            if (s != null && s.ping()) return s;
            CountDownLatch latch = new CountDownLatch(1);
            mPendingLatch.set(latch);
            try {
                Bundle args = new Bundle();
                args.setClassLoader(YlHandshake.class.getClassLoader());
                args.putBinder(YlHandshake.KEY_HANDSHAKE, handshakeBinder());
                Bundle out = ctx.getContentResolver()
                        .call(YlBootProvider.URI, YlBootProvider.METHOD_START, null, args);
                if (out != null && out.getString("error") != null) {
                    Log.e(TAG, "Provider 拉起失败: " + out.getString("error"));
                }
                if (!latch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "等待服务端握手超时");
                }
            } catch (Throwable t) {
                Log.e(TAG, "拉起服务端异常", t);
            } finally {
                mPendingLatch.compareAndSet(latch, null);
            }
            return mService;
        }
    }

    
    void armHandshake(IBinder handshake, CountDownLatch latch) {
        mPendingLatch.set(latch);
        
        
    }

    
    void onServiceBinder(IBinder binder) {
        if (binder == null) return;
        YlService.Proxy proxy = new YlService.Proxy(binder);
        mService = proxy;
        mServiceBinder = binder;
        mStarted = true;

        CountDownLatch latch = mPendingLatch.getAndSet(null);
        if (latch != null) latch.countDown();

        Log.i(TAG, "已连接特权服务 serverUid=" + proxy.getServerUid());
        mMain.post(() -> {
            for (BinderReceivedListener l : mReceivedListeners) {
                try { l.onBinderReceived(); } catch (Throwable ignored) {}
            }
        });
        try {
            binder.linkToDeath(() -> {
                Log.w(TAG, "特权服务已断开");
                mService = null;
                mServiceBinder = null;
                mStarted = false;
                mMain.post(() -> {
                    for (BinderDeadListener l : mDeadListeners) {
                        try { l.onBinderDead(); } catch (Throwable ignored) {}
                    }
                });
            }, 0);
        } catch (Throwable t) {
            Log.w(TAG, "linkToDeath 失败", t);
        }
    }

    IBinder serviceBinder() {
        return mServiceBinder;
    }

    boolean isStarted() {
        return mStarted;
    }

    
    IBinder handshakeBinder() {
        return YlHandshake.asBinder(this::onServiceBinder);
    }

    String describeState() {
        YlService.Proxy s = mService;
        return "service=" + (s == null ? "null" : "ok")
                + " ping=" + (s != null && s.ping())
                + " started=" + mStarted
                + " apk=" + YlPrivLauncher.apkPath()
                + " app_process=" + YlPrivLauncher.appProcessPath();
    }

    private Context appContext() {
        YouLongApp app = YouLongApp.instance();
        return app;
    }

    
    public static java.io.File ensureDir(java.io.File dir) {
        if (dir != null && !dir.exists()) dir.mkdirs();
        return dir;
    }
}
