package com.youlong.priv.server;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import com.youlong.priv.YlApplication;
import com.youlong.priv.YlRemoteProcess;
import com.youlong.priv.YlService;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public final class YlServerMain {

    private static final String TAG = "YlServer";

    private static volatile YlServerMain sInstance;

    private Context mContext;
    
    private boolean mInitialized;
    private final AtomicInteger mProcessSeq = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, YlRemoteProcessImpl> mProcesses =
            new ConcurrentHashMap<>();

    private YlServerMain() {}

    public static YlServerMain get() {
        YlServerMain s = sInstance;
        if (s == null) {
            synchronized (YlServerMain.class) {
                if (sInstance == null) sInstance = new YlServerMain();
                s = sInstance;
            }
        }
        return s;
    }

    
    public static void main(String[] args) {
        
        
        diag("main() 进入，args=" + java.util.Arrays.toString(args)
                + " uid=" + Process.myUid() + " pid=" + Process.myPid());
        try {
            Looper.prepareMainLooper();
            diag("Looper 就绪，开始 init()");
            get().init();
            diag("init() 完成，进入 Looper.loop()");
            Looper.loop();
        } catch (Throwable t) {
            diag("服务端启动失败: " + t);
            Log.e(TAG, "服务端启动失败", t);
            System.exit(1);
        }
    }

    
    static void diag(String msg) {
        String line = "[" + new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS",
                java.util.Locale.US).format(new java.util.Date()) + "] " + msg + "\n";
        java.io.FileOutputStream fos = null;
        try {
            File f = new File("/data/local/tmp/ylpriv_server.log");
            fos = new java.io.FileOutputStream(f, true);
            fos.write(line.getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) { try { fos.close(); } catch (Throwable ignored) {} }
        }
        Log.i(TAG, msg);
    }

    private void init() {
        if (mInitialized) return;
        mInitialized = true;
        
        
        mContext = YlSystemContext.get();
        Log.i(TAG, "服务端已就绪，Context="
                + (mContext == null ? "null(退回文件存储)" : mContext.getPackageName())
                + "，授权记录=" + YlPermission.describeStore(this));
    }

    public Context context() {
        return mContext;
    }

    public IBinder asBinder() {
        return YlService.asBinder(new YlService.Stub() {
            @Override
            public boolean ping() {
                return true;
            }

            @Override
            public boolean checkPermission(String permission) {
                return YlPermission.check(YlServerMain.this, permission);
            }

            @Override
            public void requestPermission(int requestCode, String permission) {
                YlPermission.request(YlServerMain.this, requestCode, permission);
            }

            @Override
            public void attachApplication(IBinder callback, Bundle args) {
                YlPermission.attachApplication(YlServerMain.this,
                        callback == null ? null : new YlApplication(callback), args);
            }

            @Override
            public IBinder newProcess(String[] cmd, String[] env, String dir,
                                      ParcelFileDescriptor stdinRead,
                                      ParcelFileDescriptor stdoutWrite,
                                      ParcelFileDescriptor stderrWrite) {
                int callerUid = android.os.Binder.getCallingUid();
                if (!YlPermission.checkUid(YlServerMain.this, callerUid)) {
                    Log.w(TAG, "拒绝未授权 uid 的进程请求: " + callerUid);
                    return null;
                }
                int id = mProcessSeq.incrementAndGet();
                YlRemoteProcessImpl impl = new YlRemoteProcessImpl(id, cmd, env, dir,
                        stdinRead, stdoutWrite, stderrWrite);
                if (!impl.start()) {
                    Log.e(TAG, "拉起进程失败: " + java.util.Arrays.toString(cmd));
                    return null;
                }
                mProcesses.put(id, impl);
                return YlRemoteProcess.asBinder(impl);
            }

            @Override
            public int getServerUid() {
                return Process.myUid();
            }
        });
    }

    void forget(int id) {
        mProcesses.remove(id);
    }

    
    static final class YlRemoteProcessImpl implements YlRemoteProcess.Stub {

        private final int mId;
        private final String[] mCmd;
        private final String[] mEnv;
        private final String mDir;
        private final ParcelFileDescriptor mStdinRead;
        private final ParcelFileDescriptor mStdoutWrite;
        private final ParcelFileDescriptor mStderrWrite;

        private java.lang.Process mProc;
        private int mExit = -1;
        private boolean mDone;

        YlRemoteProcessImpl(int id, String[] cmd, String[] env, String dir,
                            ParcelFileDescriptor stdinRead,
                            ParcelFileDescriptor stdoutWrite,
                            ParcelFileDescriptor stderrWrite) {
            this.mId = id;
            this.mCmd = cmd;
            this.mEnv = env;
            this.mDir = YlService.normalizeDir(dir);
            this.mStdinRead = stdinRead;
            this.mStdoutWrite = stdoutWrite;
            this.mStderrWrite = stderrWrite;
        }

        synchronized boolean start() {
            try {
                
                //
                
                
                
                
                StringBuilder script = new StringBuilder();
                for (int i = 0; i < mCmd.length; i++) {
                    if (i > 0) script.append(' ');
                    script.append(shellQuote(mCmd[i]));
                }
                String inPath = fdPath(mStdinRead);
                String outPath = fdPath(mStdoutWrite);
                String errPath = fdPath(mStderrWrite);
                if (inPath != null) script.append(" < ").append(inPath);
                if (outPath != null) script.append(" > ").append(outPath);
                if (errPath != null) script.append(" 2> ").append(errPath);
                Log.i(TAG, "进程脚本: " + script);

                ProcessBuilder pb = new ProcessBuilder("sh", "-c", script.toString());
                if (mEnv != null && mEnv.length > 0) {
                    pb.environment().clear();
                    for (String e : mEnv) {
                        int i = e.indexOf('=');
                        if (i > 0) pb.environment().put(e.substring(0, i), e.substring(i + 1));
                    }
                }
                if (mDir != null) pb.directory(new File(mDir));
                
                
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                pb.redirectError(ProcessBuilder.Redirect.PIPE);

                mProc = pb.start();
                Log.i(TAG, "进程已启动 id=" + mId + " cmd=" + java.util.Arrays.toString(mCmd)
                        + " serverUid=" + Process.myUid());
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "start 失败", t);
                return false;
            }
        }

        
        private static String fdPath(ParcelFileDescriptor pfd) {
            if (pfd == null) return null;
            try {
                java.io.FileDescriptor fd = pfd.getFileDescriptor();
                java.lang.reflect.Field f = java.io.FileDescriptor.class.getDeclaredField("descriptor");
                f.setAccessible(true);
                int raw = (Integer) f.get(fd);
                if (raw < 0) return null;
                return "/proc/self/fd/" + raw;
            } catch (Throwable t) {
                Log.w(TAG, "取 fd 号失败", t);
                return null;
            }
        }

        private static String shellQuote(String s) {
            if (s == null) return "''";
            return "'" + s.replace("'", "'\\''") + "'";
        }

        
        @Override public synchronized ParcelFileDescriptor getOutputStream() { return null; }

        @Override public synchronized ParcelFileDescriptor getInputStream() { return null; }

        @Override public synchronized ParcelFileDescriptor getErrorStream() { return null; }

        @Override
        public synchronized int waitFor() {
            if (mDone) return mExit;
            try {
                mExit = mProc.waitFor();
                mDone = true;
            } catch (Throwable ignored) {
                mDone = true;
            }
            return mExit;
        }

        @Override
        public synchronized int exitValue() {
            if (mDone) return mExit;
            try {
                mExit = mProc.exitValue();
                mDone = true;
            } catch (Throwable ignored) {
            }
            return mExit;
        }

        @Override
        public synchronized boolean alive() {
            if (mDone) return false;
            try {
                mProc.exitValue();
                mDone = true;
                return false;
            } catch (Throwable ignored) {
                return true;
            }
        }

        @Override
        public synchronized void destroy() {
            try {
                mProc.destroy();
            } catch (Throwable ignored) {
            }
            YlServerMain.get().forget(mId);
        }
    }
}
