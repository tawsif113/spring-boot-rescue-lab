package com.tawsif.rescuelab.product;

import java.util.UUID;

final class ProductCacheKeys {

    private static final String PREFIX = "rescue:catalog:product:v1:";

    private ProductCacheKeys() {
    }

    static String data(UUID productId) {
        return PREFIX + productId;
    }

    static String lock(UUID productId) {
        return data(productId) + ":lock";
    }
}
