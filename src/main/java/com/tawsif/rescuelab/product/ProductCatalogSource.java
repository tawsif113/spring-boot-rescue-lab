package com.tawsif.rescuelab.product;

import java.util.UUID;

@FunctionalInterface
public interface ProductCatalogSource {

    ProductResponse load(UUID productId);
}
