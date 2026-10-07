package com.youlong.hd;


public final class GuardNative {

    private static volatile boolean sLoaded = false;
    private static final Object sLoadLock = new Object();

    private GuardNative() {}

    private static void ensureLoaded() {
        if (sLoaded) return;
        synchronized (sLoadLock) {
            if (sLoaded) return;
            try {
                System.loadLibrary("nativecrypto");
            } catch (Throwable ignored) {
                
                
            }
            sLoaded = true;
        }
    }

    
    public static int startSentinel(int mainPid, int guardPid, String signalDir) {
        ensureLoaded();
        try {
            return nativeStartSentinel(mainPid, guardPid, signalDir);
        } catch (Throwable t) {
            return -1;
        }
    }

    
    public static void stopSentinel() {
        ensureLoaded();
        try {
            nativeStopSentinel();
        } catch (Throwable ignored) {}
    }

    private static native int nativeStartSentinel(int mainPid, int guardPid, String signalDir);

    private static native void nativeStopSentinel();
}
