package com.youlong.priv.server;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;


final class YlSystemContext {

    private static final String TAG = "YlSystemContext";

    private YlSystemContext() {}

    
    static Context get() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            if (thread == null) return null;
            Object ctx = at.getMethod("getSystemContext").invoke(thread);
            if (ctx instanceof Context) {
                return (Context) ctx;
            }
        } catch (Throwable t) {
            Log.w(TAG, "反射取系统 Context 失败（不影响特权执行）: " + t);
        }
        return null;
    }

    
    static File storeDir(Context ctx) {
        if (ctx != null) {
            try {
                File dir = new File(ctx.getFilesDir(), "ylpriv");
                if (dir.exists() || dir.mkdirs()) return dir;
            } catch (Throwable ignored) {
            }
        }
        File fallback = new File("/data/local/tmp/ylpriv");
        if (!fallback.exists()) fallback.mkdirs();
        return fallback;
    }

    
    static Properties loadProps(File f) {
        Properties p = new Properties();
        if (f == null || !f.exists()) return p;
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            p.load(in);
        } catch (Throwable t) {
            Log.w(TAG, "读属性失败: " + f, t);
        } finally {
            closeQuietly(in);
        }
        return p;
    }

    
    static void saveProps(File f, Properties p) {
        if (f == null || p == null) return;
        OutputStream out = null;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new FileOutputStream(f);
            p.store(out, "youlong privilege auth");
        } catch (Throwable t) {
            Log.w(TAG, "写属性失败: " + f, t);
        } finally {
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (Throwable ignored) {}
    }
}
