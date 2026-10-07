package com.youlong.hd;


public final class NativeCrypto {

    static {
        try {
            System.loadLibrary("nativecrypto");
        } catch (UnsatisfiedLinkError e) {
            
            
        }
    }

    private NativeCrypto() {}

    
    public static native boolean isNativeLoaded();

    
    public static native byte[] deriveSeedForFallback();

    
    public static native byte[] deriveSaltForFallback();

    
    public static native byte[] deriveKey(byte[] fingerprint);

    
    public static native byte[] decrypt(byte[] iv, byte[] ciphertext, byte[] fingerprint);

    
    public static native boolean isTracerAttached();

    
    public static native boolean detectFrida();

    
    static {
        
    }
}
