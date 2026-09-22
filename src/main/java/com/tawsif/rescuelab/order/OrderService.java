package com.tawsif.rescuelab.order;

import com.tawsif.rescuelab.product.Product;
import com.tawsif.rescuelab.product.ProductService;
import com.tawsif.rescuelab.shared.PageResponse;
import com.tawsif.rescuelab.shared.ResourceNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseOrderRepository orderRepository;
    private final ProductService productService;
    private final OrderIdempotencyRecordRepository idempotencyRepository;
    private final IdempotencyLock idempotencyLock;
    private final Clock clock;
    private final Duration idempotencyTtl;

    public OrderService(
            PurchaseOrderRepository orderRepository,
            ProductService productService,
            OrderIdempotencyRecordRepository idempotencyRepository,
            IdempotencyLock idempotencyLock,
            Clock clock,
            @Value("${rescue-lab.idempotency.ttl:PT24H}") Duration idempotencyTtl
    ) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencyLock = idempotencyLock;
        this.clock = clock;
        this.idempotencyTtl = idempotencyTtl;
    }

    @Transactional
    public OrderCreationResult create(
            UUID customerId,
            String rawIdempotencyKey,
            CreateOrderRequest request
    ) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String requestFingerprint = IdempotencyFingerprint.of(request);
        Instant now = clock.instant();

        idempotencyLock.acquire(customerId, idempotencyKey);

        Optional<OrderIdempotencyRecord> existingRecord = idempotencyRepository
                .findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);

        if (existingRecord.isPresent() && !existingRecord.get().isExpiredAt(now)) {
            OrderIdempotencyRecord record = existingRecord.get();
            if (!record.hasFingerprint(requestFingerprint)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new OrderCreationResult(OrderResponse.from(record.getOrder()), true);
        }

        existingRecord.ifPresent(record -> {
            idempotencyRepository.delete(record);
            idempotencyRepository.flush();
        });

        PurchaseOrder order = new PurchaseOrder(customerId);

        for (OrderLineRequest line : request.items()) {
            Product product = productService.reserveForOrder(line.productId(), line.quantity());
            order.addItem(product, line.quantity());
        }

        PurchaseOrder savedOrder = orderRepository.save(order);
        idempotencyRepository.save(new OrderIdempotencyRecord(
                customerId,
                idempotencyKey,
                requestFingerprint,
                savedOrder,
                now,
                now.plus(idempotencyTtl)
        ));

        return new OrderCreationResult(OrderResponse.from(savedOrder), false);
    }

    private String normalizeIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header must not be blank");
        }

        String normalized = rawIdempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key header must be at most 128 characters");
        }
        return normalized;
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

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAll(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<UUID> idPage = orderRepository.findPageIds(pageable);

        if (idPage.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    idPage.getNumber(),
                    idPage.getSize(),
                    idPage.getTotalElements(),
                    idPage.getTotalPages()
            );
        }

        Map<UUID, PurchaseOrder> ordersById = orderRepository
                .findAllWithItemsAndProductsByIdIn(idPage.getContent())
                .stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity()));

        List<OrderResponse> content = idPage.getContent().stream()
                .map(ordersById::get)
                .map(OrderResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                idPage.getNumber(),
                idPage.getSize(),
                idPage.getTotalElements(),
                idPage.getTotalPages()
        );
    }
}
