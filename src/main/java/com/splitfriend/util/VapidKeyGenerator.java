package com.splitfriend.util;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.security.*;
import java.util.Base64;

/**
 * Utility to generate VAPID key pair for Web Push notifications.
 * Run this once to generate keys, then add them to application.yml.
 */
public class VapidKeyGenerator {

    public static void main(String[] args) {
        try {
            Security.addProvider(new BouncyCastleProvider());

            ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("prime256v1");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
            keyPairGenerator.initialize(parameterSpec, new SecureRandom());

            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

            String publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes);
            String privateKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKeyBytes);

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
