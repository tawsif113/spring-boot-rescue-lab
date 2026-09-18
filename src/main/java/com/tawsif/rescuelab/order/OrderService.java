package com.tawsif.rescuelab.order;

import com.tawsif.rescuelab.product.Product;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.shared.PageResponse;
import com.tawsif.rescuelab.shared.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseOrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(PurchaseOrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * Fragile baseline: the endpoint has no idempotency protection and stock is
     * reserved with an unsafe read-check-write sequence. INC-002 and INC-003
     * will address these defects independently.
     */
    @Transactional
    public OrderResponse create(UUID customerId, CreateOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder(customerId);

        for (OrderLineRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", line.productId()));
            product.reserve(line.quantity());
            order.addItem(product, line.quantity());
        }

        return OrderResponse.from(orderRepository.save(order));
    }

    /**
     * Fragile baseline: access is not restricted to the requesting customer.
     * INC-004 will add identity-based ownership authorization.
     */
    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return OrderResponse.from(order);
    }

    /**
     * Fragile baseline: mapping lazy order items and their products creates an
     * N+1 query pattern. INC-001 will measure and optimize this read path.
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAll(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<PurchaseOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(orders, OrderResponse::from);
    }
}

