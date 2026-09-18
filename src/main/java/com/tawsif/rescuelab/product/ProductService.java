package com.tawsif.rescuelab.product;

import com.tawsif.rescuelab.shared.ConflictException;
import com.tawsif.rescuelab.shared.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
}

