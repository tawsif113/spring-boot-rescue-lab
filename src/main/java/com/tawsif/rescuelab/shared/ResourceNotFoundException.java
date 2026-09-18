package com.tawsif.rescuelab.shared;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, UUID id) {
        super(resourceType + " " + id + " was not found");
    }
}

