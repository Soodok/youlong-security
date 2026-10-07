package com.youlong.priv;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;


public final class YlHandshake {

    
    public static final int TX_DELIVER = 1;

    public static final String DESCRIPTOR = "com.youlong.priv.IYlHandshake";

    
    public static final String KEY_HANDSHAKE = "handshake";

    private YlHandshake() {}

    
    public interface Stub {
        void onServiceBinder(IBinder service);
    }

    
    public static IBinder asBinder(final Stub impl) {
        return new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                YlProtocol.checkDescriptor(data, DESCRIPTOR);
                if (code == TX_DELIVER) {
                    IBinder service = data.readStrongBinder();
                    impl.onServiceBinder(service);
                    reply.writeNoException();
                    return true;
                }
                return false;
            }
        };
    }

    
    public static void deliver(IBinder handshake, IBinder service) {
        if (handshake == null || service == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            YlProtocol.writeDescriptor(data, DESCRIPTOR);
            data.writeStrongBinder(service);
            handshake.transact(TX_DELIVER, data, reply, 0);
            reply.readException();
        } catch (Throwable ignored) {
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    
    public static IBinder fromBundle(android.os.Bundle bundle) {
        if (bundle == null) return null;
        bundle.setClassLoader(YlHandshake.class.getClassLoader());
        return bundle.getBinder(KEY_HANDSHAKE);
    }
}
