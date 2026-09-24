package com.tawsif.rescuelab.product;

import com.tawsif.rescuelab.shared.ConflictException;
import com.tawsif.rescuelab.shared.InsufficientStockException;
import com.tawsif.rescuelab.shared.ResourceNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheInvalidator cacheInvalidator;
    private final Clock clock;

    public ProductService(
            ProductRepository productRepository,
            ProductCacheInvalidator cacheInvalidator,
            Clock clock
    ) {
        this.productRepository = productRepository;
        this.cacheInvalidator = cacheInvalidator;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new ConflictException("A product with SKU " + normalizedSku + " already exists");
        }

        Product product = new Product(
                normalizedSku,
                request.name().trim(),
                request.unitPrice(),
                request.availableStock()
        );
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return ProductResponse.from(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id)));
    }

    /**
     * Performs the stock check and decrement as one PostgreSQL statement. The
     * surrounding order transaction is mandatory so a later order failure
     * rolls the reservation back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Product reserveForOrder(UUID productId, int quantity) {
        int updatedRows = productRepository.reserveIfAvailable(productId, quantity, clock.instant());
        if (updatedRows == 0) {
            int available = productRepository.findAvailableStockById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
            throw new InsufficientStockException(productId, available, quantity);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        cacheInvalidator.evictAfterCommit(productId);
        return product;
    }
}
