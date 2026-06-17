package org.dawid.cisowski.walletassistant.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignature() {
    }

    static String calculate(String canonicalString, byte[] secret) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            var signature = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalArgumentException("Unable to compute HMAC signature", exception);
        }
    }

    static boolean verify(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
