package com.example.android.bluetoothlegatt;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Verification {

    private static final int VALID_CHARS = 5;

    String charsFromHASH(String message, String HASH){
        StringBuilder msg = new StringBuilder();
        String[] separatedMessage = message.split(" ");
        for (int i=0; i< VALID_CHARS; i++){
            int index = Integer.parseInt( separatedMessage[i + 1] );
            msg.append(HASH.charAt( index ));
        }

        return msg.toString();
    }

    String SHA256(String password) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        md.update(password.getBytes());
        byte[] digest = md.digest();

        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest)
            hex.append(String.format("%02x", b & 0xFF));
        return hex.toString();


    }
}
