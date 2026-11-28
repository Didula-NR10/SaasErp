package com.saaserp.erp.platform.authorization.domain;

import java.util.Objects;

public record Permission(String value) {
    public Permission {
        Objects.requireNonNull(value, "Permission is required");
        if (!value.matches("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*){1,5}")) {
            throw new IllegalArgumentException("Invalid permission");
        }
    }
}
