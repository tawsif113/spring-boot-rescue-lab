package com.tawsif.rescuelab.product;

import com.tawsif.rescuelab.shared.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, String name, BigDecimal unitPrice, int availableStock) {
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.availableStock = availableStock;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Preserves the original read-check-write behavior for the INC-003 race
     * harness and focused domain tests. Production order creation uses the
     * repository's atomic conditional update instead.
     */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        if (availableStock < quantity) {
            throw new InsufficientStockException(id, availableStock, quantity);
        }
        availableStock -= quantity;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
