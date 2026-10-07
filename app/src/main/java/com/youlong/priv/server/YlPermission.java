package com.youlong.priv.server;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.youlong.priv.YlApplication;
import com.youlong.priv.YlProtocol;

import java.io.File;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


final class YlPermission {

    private static final String TAG = "YlPermission";
    private static final String PREFS_CTX = "yl_priv_auth";
    private static final String AUTH_FILE = "auth.properties";
    private static final String KEY_PREFIX = "granted_uid_";

    
    private static volatile int sClientAppUid = -1;

    private static final Set<YlApplication> CALLBACKS =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<YlApplication, Boolean>());

    private YlPermission() {}

    private static int serverUid() {
        return android.os.Process.myUid();
    }

    
    static boolean checkUid(YlServerMain server, int uid) {
        if (uid == serverUid()) return true;
        if (uid == 0) return true;
        if (sClientAppUid > 0 && uid == sClientAppUid) return true;
        Context ctx = server.context();
        if (ctx != null) {
            try {
                if (uid == ctx.getApplicationInfo().uid) return true;
            } catch (Throwable ignored) {
            }
        }
        return readGrant(server, uid);
    }

    static boolean check(YlServerMain server, String permission) {
        return checkUid(server, android.os.Binder.getCallingUid());
    }

    
    static void grant(YlServerMain server, int uid, boolean allowed) {
        Context ctx = server.context();
        if (ctx != null) {
            try {
                ctx.getSharedPreferences(PREFS_CTX, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_PREFIX + uid, allowed).apply();
                Log.i(TAG, "授权记录写入 prefs uid=" + uid + " allowed=" + allowed);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "写 prefs 失败，退回文件", t);
            }
        }
        Properties p = YlSystemContext.loadProps(authFile(server));
        p.setProperty(KEY_PREFIX + uid, String.valueOf(allowed));
        YlSystemContext.saveProps(authFile(server), p);
        Log.i(TAG, "授权记录写入文件 uid=" + uid + " allowed=" + allowed);
    }

    
    static void attachApplication(YlServerMain server, YlApplication app, Bundle args) {
        int uid = android.os.Binder.getCallingUid();
        if (app == null) return;
        CALLBACKS.add(app);
        if (args != null) {
            int reported = args.getInt(YlProtocol.KEY_CLIENT_UID, -1);
            if (reported > 0) sClientAppUid = reported;
        }
        if (sClientAppUid <= 0 && uid > 0) sClientAppUid = uid;
        try {
            app.onServerReady();
        } catch (Throwable t) {
            Log.w(TAG, "回调 onServerReady 失败", t);
        }
        Log.i(TAG, "客户端已挂载 uid=" + uid
                + " 记录的应用uid=" + sClientAppUid
                + " pkg=" + (args == null ? "?" : args.getString(YlProtocol.KEY_PACKAGE_NAME)));
    }

    
    static void request(YlServerMain server, int requestCode, String permission) {
        int uid = android.os.Binder.getCallingUid();
        Log.i(TAG, "收到授权请求 uid=" + uid + " permission=" + permission);
        for (YlApplication cb : CALLBACKS) {
            try {
                cb.onPermissionResult(requestCode, permission, true);
            } catch (Throwable t) {
                CALLBACKS.remove(cb);
            }
        }
    }

    private static boolean readGrant(YlServerMain server, int uid) {
        Context ctx = server.context();
        if (ctx != null) {
            try {
                return ctx.getSharedPreferences(PREFS_CTX, Context.MODE_PRIVATE)
                        .getBoolean(KEY_PREFIX + uid, false);
            } catch (Throwable ignored) {
            }
        }
        Properties p = YlSystemContext.loadProps(authFile(server));
        return Boolean.parseBoolean(p.getProperty(KEY_PREFIX + uid, "false"));
    }

    private static File authFile(YlServerMain server) {
        return new File(YlSystemContext.storeDir(server.context()), AUTH_FILE);
    }

    
    static String describeStore(YlServerMain server) {
        Context ctx = server.context();
        if (ctx != null) {
            try {
                return new File(ctx.getDataDir(),
                        "shared_prefs/" + PREFS_CTX + ".xml").getAbsolutePath();
            } catch (Throwable ignored) {
            }
        }
        return authFile(server).getAbsolutePath();
    }
}
