package com.youlong.priv;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;


public final class YlProtocol {

    private YlProtocol() {}

    
    public static final int SERVER_VERSION = 1;

    
    public static final String PERMISSION_PRIVILEGED = "privileged";

    
    public static final String KEY_API_VERSION = "api_version";
    public static final String KEY_PACKAGE_NAME = "package_name";
    public static final String KEY_CLIENT_UID = "client_uid";
    public static final String KEY_PERMISSION_GRANTED = "permission_granted";
    public static final String KEY_SERVER_UID = "server_uid";
    public static final String KEY_SERVER_VERSION = "server_version";
    public static final String KEY_PERMISSION = "permission";
    public static final String KEY_ALLOWED = "allowed";

    
    /** boolean ping() */
    public static final int TX_PING = 1;
    /** boolean checkPermission(String) */
    public static final int TX_CHECK_PERMISSION = 2;
    /** void requestPermission(int requestCode, String permission) */
    public static final int TX_REQUEST_PERMISSION = 3;
    /** void attachApplication(IYlApplication, Bundle) */
    public static final int TX_ATTACH = 4;
    
    public static final int TX_NEW_PROCESS = 5;
    /** int getServerUid() */
    public static final int TX_GET_SERVER_UID = 6;

    
    /** ParcelFileDescriptor getOutputStream() */
    public static final int TX_PROC_OUT = 20;
    /** ParcelFileDescriptor getInputStream() */
    public static final int TX_PROC_IN = 21;
    /** ParcelFileDescriptor getErrorStream() */
    public static final int TX_PROC_ERR = 22;
    /** int waitFor() */
    public static final int TX_PROC_WAIT = 23;
    /** int exitValue() */
    public static final int TX_PROC_EXIT = 24;
    /** void destroy() */
    public static final int TX_PROC_DESTROY = 25;
    /** boolean alive() */
    public static final int TX_PROC_ALIVE = 26;

    
    /** void onServerReady() */
    public static final int TX_APP_SERVER_READY = 40;
    /** void onPermissionResult(int requestCode, String permission, boolean allowed) */
    public static final int TX_APP_PERM_RESULT = 41;

    
    public static final String DESCRIPTOR_SERVICE = "com.youlong.priv.IYlService";
    public static final String DESCRIPTOR_PROCESS = "com.youlong.priv.IYlRemoteProcess";
    public static final String DESCRIPTOR_APPLICATION = "com.youlong.priv.IYlApplication";

    
    public static void writeDescriptor(Parcel data, String descriptor) {
        data.writeInterfaceToken(descriptor);
    }

    
    public static void checkDescriptor(Parcel data, String descriptor) throws RemoteException {
        data.enforceInterface(descriptor);
    }

    
    public static boolean pingBinder(IBinder binder) {
        if (binder == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            writeDescriptor(data, DESCRIPTOR_SERVICE);
            binder.transact(TX_PING, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
