package com.youlong.priv;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;


public final class YlRemoteProcess {

    private final IBinder mRemote;

    public YlRemoteProcess(IBinder remote) {
        this.mRemote = remote;
    }

    
    public IBinder asBinder() {
        return mRemote;
    }

    
    public ParcelFileDescriptor getOutputStream() throws RemoteException {
        return transactFd(YlProtocol.TX_PROC_OUT);
    }

    
    public ParcelFileDescriptor getInputStream() throws RemoteException {
        return transactFd(YlProtocol.TX_PROC_IN);
    }

    
    public ParcelFileDescriptor getErrorStream() throws RemoteException {
        return transactFd(YlProtocol.TX_PROC_ERR);
    }

    
    public int waitFor() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
            mRemote.transact(YlProtocol.TX_PROC_WAIT, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    
    public int exitValue() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
            mRemote.transact(YlProtocol.TX_PROC_EXIT, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    
    public boolean alive() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
            mRemote.transact(YlProtocol.TX_PROC_ALIVE, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    
    public void destroy() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
            mRemote.transact(YlProtocol.TX_PROC_DESTROY, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private ParcelFileDescriptor transactFd(int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
            mRemote.transact(code, data, reply, 0);
            reply.readException();
            
            return reply.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(reply)
                    : null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    // ==================================================================
    
    // ==================================================================

    
    public interface Stub {
        ParcelFileDescriptor getOutputStream();

        ParcelFileDescriptor getInputStream();

        ParcelFileDescriptor getErrorStream();

        int waitFor();

        int exitValue();

        boolean alive();

        void destroy();
    }

    
    public static IBinder asBinder(final Stub impl) {
        return new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                YlProtocol.checkDescriptor(data, YlProtocol.DESCRIPTOR_PROCESS);
                switch (code) {
                    case YlProtocol.TX_PROC_OUT:
                        writeFd(reply, impl.getOutputStream());
                        return true;
                    case YlProtocol.TX_PROC_IN:
                        writeFd(reply, impl.getInputStream());
                        return true;
                    case YlProtocol.TX_PROC_ERR:
                        writeFd(reply, impl.getErrorStream());
                        return true;
                    case YlProtocol.TX_PROC_WAIT:
                        reply.writeNoException();
                        reply.writeInt(impl.waitFor());
                        return true;
                    case YlProtocol.TX_PROC_EXIT:
                        reply.writeNoException();
                        reply.writeInt(impl.exitValue());
                        return true;
                    case YlProtocol.TX_PROC_ALIVE:
                        reply.writeNoException();
                        reply.writeInt(impl.alive() ? 1 : 0);
                        return true;
                    case YlProtocol.TX_PROC_DESTROY:
                        impl.destroy();
                        reply.writeNoException();
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private static void writeFd(Parcel reply, ParcelFileDescriptor pfd) {
        reply.writeNoException();
        if (pfd == null) {
            reply.writeInt(0);
        } else {
            reply.writeInt(1);
            pfd.writeToParcel(reply, 0);
        }
    }
}
