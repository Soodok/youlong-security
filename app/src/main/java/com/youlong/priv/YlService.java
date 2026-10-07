package com.youlong.priv;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public final class YlService {

    private YlService() {}

    
    public static final int SERVER_VERSION = YlProtocol.SERVER_VERSION;

    
    public static final String PERMISSION_PRIVILEGED = YlProtocol.PERMISSION_PRIVILEGED;

    
    public static final int REQ_PERMISSION = 1001;

    
    public static final String KEY_CALLBACK = "callback";

    // ==================================================================
    
    // ==================================================================

    
    public static final class Proxy {
        private final IBinder mRemote;

        public Proxy(IBinder remote) {
            this.mRemote = remote;
        }

        public IBinder asBinder() {
            return mRemote;
        }

        
        public boolean ping() {
            return YlProtocol.pingBinder(mRemote);
        }

        
        public boolean checkPermission(String permission) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                data.writeString(permission);
                mRemote.transact(YlProtocol.TX_CHECK_PERMISSION, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } catch (Throwable t) {
                return false;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        
        public void requestPermission(int requestCode, String permission) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                data.writeInt(requestCode);
                data.writeString(permission);
                mRemote.transact(YlProtocol.TX_REQUEST_PERMISSION, data, reply, 0);
                reply.readException();
            } catch (Throwable ignored) {
                
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        
        public void attachApplication(IBinder callback, String packageName) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                data.writeStrongBinder(callback);
                Bundle args = new Bundle();
                args.putInt(YlProtocol.KEY_API_VERSION, SERVER_VERSION);
                args.putString(YlProtocol.KEY_PACKAGE_NAME, packageName);
                args.writeToParcel(data, 0);
                mRemote.transact(YlProtocol.TX_ATTACH, data, reply, 0);
                reply.readException();
            } catch (Throwable ignored) {
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        
        public int getServerUid() {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                mRemote.transact(YlProtocol.TX_GET_SERVER_UID, data, reply, 0);
                reply.readException();
                return reply.readInt();
            } catch (Throwable t) {
                return -1;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        
        public Process newProcess(String[] cmd, String[] env, String dir)
                throws RemoteException {
            ParcelFileDescriptor[] stdinPipe = null;
            ParcelFileDescriptor[] stdoutPipe = null;
            ParcelFileDescriptor[] stderrPipe = null;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                stdinPipe = ParcelFileDescriptor.createReliablePipe();
                stdoutPipe = ParcelFileDescriptor.createReliablePipe();
                stderrPipe = ParcelFileDescriptor.createReliablePipe();

                YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                data.writeStringArray(cmd);
                data.writeStringArray(env);
                data.writeString(dir);
                stdinPipe[0].writeToParcel(data, 0);    
                stdoutPipe[1].writeToParcel(data, 0);   
                stderrPipe[1].writeToParcel(data, 0);   

                mRemote.transact(YlProtocol.TX_NEW_PROCESS, data, reply, 0);
                reply.readException();
                IBinder procBinder = reply.readStrongBinder();
                if (procBinder == null) {
                    throw new RemoteException("服务端未返回远程进程（可能未授权）");
                }
                
                closeQuietly(stdinPipe[0]);
                closeQuietly(stdoutPipe[1]);
                closeQuietly(stderrPipe[1]);
                return new YlProcess(new YlRemoteProcess(procBinder),
                        stdinPipe[1], stdoutPipe[0], stderrPipe[0]);
            } catch (RemoteException e) {
                closeQuietly(stdinPipe == null ? null : stdinPipe[0]);
                closeQuietly(stdoutPipe == null ? null : stdoutPipe[1]);
                closeQuietly(stderrPipe == null ? null : stderrPipe[1]);
                closeQuietly(stdinPipe == null ? null : stdinPipe[1]);
                closeQuietly(stdoutPipe == null ? null : stdoutPipe[0]);
                closeQuietly(stderrPipe == null ? null : stderrPipe[0]);
                throw e;
            } catch (Throwable t) {
                throw new RemoteException("建管道/发起进程失败: " + t);
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        private static void closeQuietly(ParcelFileDescriptor p) {
            if (p == null) return;
            try { p.close(); } catch (Throwable ignored) {}
        }
    }

    // ==================================================================
    
    // ==================================================================

    
    public static final class YlProcess extends Process {
        private final YlRemoteProcess mRemote;
        private final android.os.ParcelFileDescriptor mStdinWrite;   
        private final android.os.ParcelFileDescriptor mStdoutRead;   
        private final android.os.ParcelFileDescriptor mStderrRead;   
        private InputStream mIn;
        private OutputStream mOut;
        private InputStream mErr;
        private int mExit = -1;
        private boolean mDone;

        YlProcess(YlRemoteProcess remote,
                  android.os.ParcelFileDescriptor stdinWrite,
                  android.os.ParcelFileDescriptor stdoutRead,
                  android.os.ParcelFileDescriptor stderrRead) {
            this.mRemote = remote;
            this.mStdinWrite = stdinWrite;
            this.mStdoutRead = stdoutRead;
            this.mStderrRead = stderrRead;
        }

        @Override
        public synchronized OutputStream getOutputStream() {
            if (mOut == null) {
                mOut = (mStdinWrite == null) ? nullOut()
                        : new android.os.ParcelFileDescriptor.AutoCloseOutputStream(mStdinWrite);
            }
            return mOut;
        }

        @Override
        public synchronized InputStream getInputStream() {
            if (mIn == null) {
                mIn = (mStdoutRead == null) ? emptyIn()
                        : new android.os.ParcelFileDescriptor.AutoCloseInputStream(mStdoutRead);
            }
            return mIn;
        }

        @Override
        public synchronized InputStream getErrorStream() {
            if (mErr == null) {
                mErr = (mStderrRead == null) ? emptyIn()
                        : new android.os.ParcelFileDescriptor.AutoCloseInputStream(mStderrRead);
            }
            return mErr;
        }

        @Override
        public synchronized int waitFor() throws InterruptedException {
            if (mDone) return mExit;
            try {
                mExit = mRemote.waitFor();
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
                if (!mRemote.alive()) {
                    mExit = mRemote.exitValue();
                    mDone = true;
                    return mExit;
                }
            } catch (Throwable ignored) {
            }
            
            throw new IllegalThreadStateException("进程尚未结束");
        }

        @Override
        public synchronized void destroy() {
            try {
                mRemote.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    // ==================================================================
    
    // ==================================================================

    
    public interface Stub {
        boolean ping();

        boolean checkPermission(String permission);

        void requestPermission(int requestCode, String permission);

        void attachApplication(IBinder callback, Bundle args);

        
        IBinder newProcess(String[] cmd, String[] env, String dir,
                           android.os.ParcelFileDescriptor stdinRead,
                           android.os.ParcelFileDescriptor stdoutWrite,
                           android.os.ParcelFileDescriptor stderrWrite) throws RemoteException;

        int getServerUid();
    }

    
    public static IBinder asBinder(final Stub impl) {
        return new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                YlProtocol.checkDescriptor(data, YlProtocol.DESCRIPTOR_SERVICE);
                switch (code) {
                    case YlProtocol.TX_PING:
                        reply.writeNoException();
                        reply.writeInt(impl.ping() ? 1 : 0);
                        return true;
                    case YlProtocol.TX_CHECK_PERMISSION: {
                        String permission = data.readString();
                        reply.writeNoException();
                        reply.writeInt(impl.checkPermission(permission) ? 1 : 0);
                        return true;
                    }
                    case YlProtocol.TX_REQUEST_PERMISSION: {
                        int requestCode = data.readInt();
                        String permission = data.readString();
                        impl.requestPermission(requestCode, permission);
                        reply.writeNoException();
                        return true;
                    }
                    case YlProtocol.TX_ATTACH: {
                        IBinder callback = data.readStrongBinder();
                        Bundle args = data.readInt() != 0
                                ? Bundle.CREATOR.createFromParcel(data) : null;
                        impl.attachApplication(callback, args);
                        reply.writeNoException();
                        return true;
                    }
                    case YlProtocol.TX_NEW_PROCESS: {
                        String[] cmd = data.createStringArray();
                        String[] env = data.createStringArray();
                        String dir = data.readString();
                        android.os.ParcelFileDescriptor stdinRead =
                                android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data);
                        android.os.ParcelFileDescriptor stdoutWrite =
                                android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data);
                        android.os.ParcelFileDescriptor stderrWrite =
                                android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data);
                        IBinder proc = null;
                        try {
                            proc = impl.newProcess(cmd, env, dir,
                                    stdinRead, stdoutWrite, stderrWrite);
                        } finally {
                            
                            closeQuietly(stdinRead);
                            closeQuietly(stdoutWrite);
                            closeQuietly(stderrWrite);
                        }
                        reply.writeNoException();
                        reply.writeStrongBinder(proc);
                        return true;
                    }
                    case YlProtocol.TX_GET_SERVER_UID:
                        reply.writeNoException();
                        reply.writeInt(impl.getServerUid());
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    
    public static String normalizeDir(String dir) {
        if (dir == null || dir.isEmpty()) return null;
        File f = new File(dir);
        return f.isDirectory() ? dir : null;
    }

    
    public static InputStream emptyIn() {
        return new java.io.ByteArrayInputStream(new byte[0]);
    }

    
    public static OutputStream nullOut() {
        return new OutputStream() {
            @Override public void write(int b) {}
            @Override public void write(byte[] b, int off, int len) {}
        };
    }

    
    public static void closeQuietly(android.os.ParcelFileDescriptor p) {
        if (p == null) return;
        try { p.close(); } catch (Throwable ignored) {}
    }
}
