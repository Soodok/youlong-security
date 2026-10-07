package com.youlong.hd;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import roro.stellar.shizuku.ShizukuCompat;


public final class ShizukuBackend {

    private static final String TAG = "YlShizuku";

    
    public static final String BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService";

    
    public static final String PACKAGE_SHIZUKU = "moe.shizuku.privileged.api";
    
    public static final String PACKAGE_SHIZUKU_DEBUG = "moe.shizuku.privileged.api.debug";

    private ShizukuBackend() {}

    
    static boolean isKnownManagerPackage(String pkg) {
        if (pkg == null) return false;
        return PACKAGE_SHIZUKU.equals(pkg) || PACKAGE_SHIZUKU_DEBUG.equals(pkg);
    }

    
    public static boolean isManagerInstalled(Context ctx) {
        if (ctx == null) return false;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : new String[]{PACKAGE_SHIZUKU, PACKAGE_SHIZUKU_DEBUG}) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // ======================================================================
    
    // ======================================================================

    private static IShizukuService service() {
        try {
            IBinder b = ShizukuCompat.INSTANCE.getBinder();
            if (b == null || !b.pingBinder()) return null;
            return IShizukuService.Stub.asInterface(b);
        } catch (Throwable t) {
            Log.w(TAG, "取 Shizuku 服务失败", t);
            return null;
        }
    }

    
    public static boolean isBinderAlive() {
        try {
            IBinder b = ShizukuCompat.INSTANCE.getBinder();
            return b != null && b.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static boolean hasPermission() {
        try {
            return ShizukuCompat.INSTANCE.checkSelfPermission() == 0;
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku 权限查询失败", t);
            return false;
        }
    }

    
    public static void requestPermission(int requestCode) {
        try {
            ShizukuCompat.INSTANCE.requestPermission(requestCode);
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku 授权请求失败", t);
        }
    }

    public static boolean shouldShowRequestPermissionRationale() {
        try {
            return ShizukuCompat.INSTANCE.shouldShowRequestPermissionRationale();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static int serverUid() {
        try {
            return ShizukuCompat.INSTANCE.getUid();
        } catch (Throwable t) {
            return -1;
        }
    }

    
    public static String secontext() {
        try {
            return ShizukuCompat.INSTANCE.getSELinuxContext();
        } catch (Throwable t) {
            return null;
        }
    }

    // ======================================================================
    
    // ======================================================================

    
    public static Process newProcess(String[] cmd, String[] env, String dir) {
        IShizukuService svc = service();
        if (svc == null) throw new IllegalStateException("Shizuku 未连接");
        try {
            IRemoteProcess remote = svc.newProcess(
                    cmd,
                    env == null ? new String[0] : env,
                    dir == null ? "/" : dir);
            if (remote == null) throw new IllegalStateException("Shizuku 未返回进程（可能未授权）");
            return new ShizukuProcess(remote);
        } catch (RemoteException e) {
            throw new RuntimeException("Shizuku 执行失败: " + e, e);
        }
    }

    
    static final class ShizukuProcess extends Process {

        private final IRemoteProcess remote;
        private OutputStream os;
        private InputStream is;

        ShizukuProcess(IRemoteProcess remote) {
            this.remote = remote;
        }

        @Override
        public OutputStream getOutputStream() {
            if (os == null) {
                try {
                    os = new ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream());
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            return os;
        }

        @Override
        public InputStream getInputStream() {
            if (is == null) {
                try {
                    is = new ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream());
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            return is;
        }

        @Override
        public InputStream getErrorStream() {
            try {
                return new ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int waitFor() throws InterruptedException {
            try {
                return remote.waitFor();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                return remote.waitForTimeout(timeout, unit.toString());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int exitValue() {
            try {
                return remote.exitValue();
            } catch (RemoteException e) {
                throw new IllegalThreadStateException("进程尚未退出: " + e);
            }
        }

        @Override
        public void destroy() {
            try {
                remote.destroy();
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            try {
                return remote.alive();
            } catch (RemoteException e) {
                return false;
            }
        }
    }
}
