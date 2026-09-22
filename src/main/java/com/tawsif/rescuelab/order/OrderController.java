package com.tawsif.rescuelab.order;

import com.tawsif.rescuelab.shared.PageResponse;
import com.tawsif.rescuelab.security.RescueUserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal RescueUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderCreationResult result = orderService.create(principal.customerId(), idempotencyKey, request);
        return ResponseEntity
                .created(URI.create("/api/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse findById(
            @AuthenticationPrincipal RescueUserPrincipal principal,
            @PathVariable UUID id
    ) {
        if (principal.isAdministrator()) {
            return orderService.findById(id);
        }
        return orderService.findByIdForCustomer(id, principal.customerId());
    }

    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @AuthenticationPrincipal RescueUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (principal.isAdministrator()) {
            return orderService.findAll(page, size);
        }
        return orderService.findAllForCustomer(principal.customerId(), page, size);
    }
}
