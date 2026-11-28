package com.saaserp.erp.platform.audit.application;

import java.util.UUID;

public final class CorrelationIdContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CorrelationIdContext() { }

    public static UUID current() {
        UUID value = CURRENT.get();
        if (value == null) {
            CURRENT.remove();
            return UUID.randomUUID();
        }
        return value;
    }

    public static Scope bind(UUID value) {
        UUID previous = CURRENT.get();
        CURRENT.set(value);
        return new Scope(previous, Thread.currentThread());
    }

    public static final class Scope implements AutoCloseable {
        private final UUID previous;
        private final Thread owner;
        private boolean closed;

        private Scope(UUID previous, Thread owner) {
            this.previous = previous;
            this.owner = owner;
        }

        @Override public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Correlation scope owner mismatch");
            if (closed) return;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            closed = true;
        }
    }
}
