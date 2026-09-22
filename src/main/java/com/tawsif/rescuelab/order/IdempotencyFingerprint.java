package com.tawsif.rescuelab.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

final class IdempotencyFingerprint {

    private IdempotencyFingerprint() {
    }

    static String of(CreateOrderRequest request) {
        String canonicalRequest = request.items().stream()
                .map(line -> line.productId() + ":" + line.quantity())
                .collect(Collectors.joining("|"));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", exception);
        }
    }
}
