package com.gffh.api.push;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Reads the PKCS#8 PEM private keys that Google service accounts and Apple .p8 files both use. */
final class PemKeys {

    private PemKeys() {}

    /**
     * {@code algorithm} is the JCA key algorithm ("RSA" for FCM, "EC" for APNs).
     * Tolerates a PEM pasted into a single-line env var with literal {@code \n}s.
     */
    static PrivateKey pkcs8(String pem, String algorithm) throws GeneralSecurityException {
        String base64 = pem
                .replace("\\n", "\n")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance(algorithm).generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("The private key is not valid base64", e);
        }
    }
}
