package com.youlong.hd;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import moe.shizuku.api.BinderContainer;
import roro.stellar.shizuku.ShizukuCompat;


public class YlShizukuProvider extends ContentProvider {

    private static final String TAG = "YlShizukuProvider";

    public static final String METHOD_SEND_BINDER = "sendBinder";
    public static final String METHOD_GET_BINDER = "getBinder";
    
    public static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    private static int rejectedCount = 0;
    private static String rejectedInfo = "无";

    public static synchronized String rejectedInfo() {
        return rejectedInfo;
    }

    private static synchronized void recordRejected(int uid, String reason) {
        rejectedCount++;
        rejectedInfo = "已拒绝 " + rejectedCount + " 次，最近一次：uid=" + uid + "（" + reason + "）";
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        
        if (info != null && !info.exported) {
            throw new IllegalStateException("YlShizukuProvider 必须 exported=true");
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_SEND_BINDER.equals(method)) {
            handleSendBinder(extras);
            return Bundle.EMPTY;
        }
        if (METHOD_GET_BINDER.equals(method)) {
            return handleGetBinder();
        }
        return null;
    }

    private void handleSendBinder(Bundle extras) {
        if (extras == null) return;
        if (!isTrustedSender()) return;

        try {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
            BinderContainer container = extras.getParcelable(EXTRA_BINDER);
            IBinder binder = container == null ? null : container.getBinder();
            if (!isShizukuServiceBinder(binder)) {
                int uid = Binder.getCallingUid();
                recordRejected(uid, "binder 不是 Shizuku 服务");
                CrashLogger.event("[特权来源] 拒绝投递：Binder 不是 Shizuku 服务（uid=" + uid + "）");
                return;
            }
            Context ctx = getContext();
            String pkg = ctx == null ? null : ctx.getPackageName();
            ShizukuCompat.INSTANCE.onBinderReceived(binder, pkg);
            CrashLogger.event("[特权来源] 已收到 Shizuku 协议 Binder（来自 uid=" + Binder.getCallingUid() + "）");
        } catch (Throwable t) {
            CrashLogger.event("[特权来源] 处理 Shizuku Binder 失败", t);
        }
    }

    private Bundle handleGetBinder() {
        if (!isTrustedSender()) return null;
        IBinder binder = ShizukuCompat.INSTANCE.getBinder();
        if (binder == null || !binder.pingBinder()) return null;
        Bundle reply = new Bundle();
        reply.putParcelable(EXTRA_BINDER, new BinderContainer(binder));
        return reply;
    }

    
    private static boolean isShizukuServiceBinder(IBinder binder) {
        if (binder == null) return false;
        try {
            if (!binder.pingBinder()) return false;
            return ShizukuBackend.BINDER_DESCRIPTOR.equals(binder.getInterfaceDescriptor());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isTrustedSender() {
        final int uid;
        try {
            uid = Binder.getCallingUid();
        } catch (Throwable t) {
            return false;
        }

        
        if (uid == 0 || uid == 1000 || uid == 2000) return true;
        
        if (uid == android.os.Process.myUid()) return true;

        Context ctx = getContext();
        if (ctx == null) return false;

        
        if (!PrivRouter.isExternalShizukuAllowed(ctx)) {
            recordRejected(uid, "未开启外部 Shizuku 许可");
            CrashLogger.event("[特权来源] 拒绝外部 Binder：未开启「允许外部 Shizuku」（uid=" + uid + "）");
            return false;
        }

        String[] pkgs;
        try {
            pkgs = ctx.getPackageManager().getPackagesForUid(uid);
        } catch (Throwable t) {
            pkgs = null;
        }
        if (pkgs != null) {
            for (String p : pkgs) {
                if (ShizukuBackend.isKnownManagerPackage(p)) {
                    CrashLogger.event("[特权来源] 接受外部 Shizuku 投递：" + p);
                    return true;
                }
            }
        }
        recordRejected(uid, "不是已知的 Shizuku 管理器");
        CrashLogger.event("[特权来源] 拒绝外部 Binder：来源不是已知 Shizuku 管理器（uid=" + uid + "）");
        return false;
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
