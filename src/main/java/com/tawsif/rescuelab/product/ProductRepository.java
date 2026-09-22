package com.tawsif.rescuelab.product;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product product
            set product.availableStock = product.availableStock - :quantity,
                product.updatedAt = :updatedAt
            where product.id = :productId
              and product.availableStock >= :quantity
            """)
    int reserveIfAvailable(
            @Param("productId") UUID productId,
            @Param("quantity") int quantity,
            @Param("updatedAt") Instant updatedAt
    );

    @Query("select product.availableStock from Product product where product.id = :productId")
    Optional<Integer> findAvailableStockById(@Param("productId") UUID productId);
}
