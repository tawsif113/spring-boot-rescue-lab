package com.tawsif.rescuelab.order;

import com.tawsif.rescuelab.shared.ConflictException;

public class IdempotencyConflictException extends ConflictException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '%s' was already used with a different request payload"
                .formatted(idempotencyKey));
    }
}
