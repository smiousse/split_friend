package com.splitfriend.util;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.security.*;
import java.util.Base64;

/**
 * Utility to generate VAPID key pair for Web Push notifications.
 * Run this once to generate keys, then add them to application.yml.
 *
 * Generates raw EC keys in Base64 URL-safe format required by web-push library:
 * - Public key: 65 bytes uncompressed EC point (0x04 || x || y)
 * - Private key: 32 bytes raw scalar value
 */
public class VapidKeyGenerator {

    public static void main(String[] args) {
        try {
            Security.addProvider(new BouncyCastleProvider());

            ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("prime256v1");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
            keyPairGenerator.initialize(parameterSpec, new SecureRandom());

            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Extract raw public key bytes (65 bytes uncompressed: 0x04 || x || y)
            ECPublicKey ecPublicKey = (ECPublicKey) keyPair.getPublic();
            byte[] rawPublicKey = ecPublicKey.getQ().getEncoded(false);

            // Extract raw private key bytes (32 bytes)
            ECPrivateKey ecPrivateKey = (ECPrivateKey) keyPair.getPrivate();
            byte[] rawPrivateKeyBytes = ecPrivateKey.getD().toByteArray();

            // Handle BigInteger sign byte - ensure exactly 32 bytes
            byte[] rawPrivateKey = new byte[32];
            if (rawPrivateKeyBytes.length == 33 && rawPrivateKeyBytes[0] == 0) {
                // Remove leading zero byte (sign byte from BigInteger)
                System.arraycopy(rawPrivateKeyBytes, 1, rawPrivateKey, 0, 32);
            } else if (rawPrivateKeyBytes.length <= 32) {
                // Pad with leading zeros if shorter
                System.arraycopy(rawPrivateKeyBytes, 0, rawPrivateKey, 32 - rawPrivateKeyBytes.length, rawPrivateKeyBytes.length);
            } else {
                throw new IllegalStateException("Unexpected private key length: " + rawPrivateKeyBytes.length);
            }

            String publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKey);
            String privateKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPrivateKey);

            System.out.println("=== VAPID Key Pair Generated ===");
            System.out.println();
            System.out.println("Add these to your application.yml:");
            System.out.println();
            System.out.println("app:");
            System.out.println("  push:");
            System.out.println("    enabled: true");
            System.out.println("    vapid:");
            System.out.println("      public-key: " + publicKeyBase64);
            System.out.println("      private-key: " + privateKeyBase64);
            System.out.println("      subject: mailto:admin@splitfriend.local");
            System.out.println();
            System.out.println("Or set environment variables:");
            System.out.println("VAPID_PUBLIC_KEY=" + publicKeyBase64);
            System.out.println("VAPID_PRIVATE_KEY=" + privateKeyBase64);

        } catch (Exception e) {
            System.err.println("Failed to generate VAPID keys: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
