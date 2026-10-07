package com.android.org.conscrypt;

import java.net.Socket;
import javax.net.ssl.SSLSocket;


public final class Conscrypt {

    private Conscrypt() {
    }

    
    public static byte[] exportKeyingMaterial(SSLSocket socket, String label, byte[] context, int length) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public static boolean isConscrypt(Socket socket) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
