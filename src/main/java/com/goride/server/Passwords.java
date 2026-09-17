package com.goride.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Băm mật khẩu PBKDF2-HMAC-SHA256 (có sẵn trong JDK). Định dạng: pbkdf2$iter$salt$hash */
public final class Passwords {
    private static final int ITERATIONS = 65536;
    private static final SecureRandom RNG = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + b64(salt) + "$" + b64(derive(password, salt, ITERATIONS));
    }

    public static boolean verify(String password, String stored) {
        if (stored == null) return false;
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] expected = Base64.getDecoder().decode(p[3]);
        return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(p[1])));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
}
