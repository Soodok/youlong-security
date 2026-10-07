package com.youlong.priv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public final class YlBootProvider extends ContentProvider {

    private static final String TAG = "YlBootProvider";

    
    public static final String AUTHORITY = "com.youlong.hd.ylpriv";

    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    
    public static final String METHOD_START = "start";

    
    public static final String METHOD_STATUS = "status";

    
    private static final long READY_TIMEOUT_MS = 15_000L;

    
    private static final Object SPAWN_LOCK = new Object();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (METHOD_STATUS.equals(method)) {
            out.putString("state", YlKernel.get().describeState());
            return out;
        }
        if (!METHOD_START.equals(method)) {
            out.putString("error", "未知方法: " + method);
            return out;
        }

        IBinder handshake = YlHandshake.fromBundle(extras);
        if (handshake == null) {
            out.putString("error", "缺少握手 Binder");
            return out;
        }

        
        YlKernel kernel = YlKernel.get();
        if (kernel.pingBinder()) {
            YlHandshake.deliver(handshake, kernel.serviceBinder());
            out.putString("result", "already-running");
            out.putInt("serverUid", kernel.getServerUid());
            return out;
        }

        synchronized (SPAWN_LOCK) {
            if (kernel.pingBinder()) {
                YlHandshake.deliver(handshake, kernel.serviceBinder());
                out.putString("result", "already-running");
                out.putInt("serverUid", kernel.getServerUid());
                return out;
            }
            
            final CountDownLatch latch = new CountDownLatch(1);
            kernel.armHandshake(handshake, latch);

            YlPrivLauncher.SpawnResult r = YlPrivLauncher.spawnServer(getContext());
            Log.i(TAG, "spawnServer ok=" + r.ok + " pid=" + r.pid + " msg=" + r.message);
            if (!r.ok) {
                out.putString("error", "拉起服务端失败: " + r.message);
                Log.e(TAG, "拉起服务端失败: " + r.message);
                return out;
            }
            try {
                boolean ok = latch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!ok) {
                    out.putString("error", "等待服务端握手超时");
                    Log.e(TAG, "等待服务端握手超时");
                    return out;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.putString("error", "握手被中断");
                return out;
            }
            out.putString("result", "started");
            out.putInt("serverUid", kernel.getServerUid());
            out.putString("pid", String.valueOf(r.pid));
            Log.i(TAG, "服务端握手完成 serverUid=" + kernel.getServerUid() + " pid=" + r.pid);
            return out;
        }
    }

    
    public static File cacheDir(android.content.Context ctx) {
        return YlKernel.ensureDir(new File(ctx.getCacheDir(), "ylpriv"));
    }

    

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
