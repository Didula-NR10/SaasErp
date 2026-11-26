package com.saaserp.erp.shared.tenant.application;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-confined context lifecycle for trusted execution boundaries.
 * Callers must verify authentication and company membership before binding.
 * Context is never inherited or propagated to asynchronous work.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * Binds a context until the returned scope is closed on this thread.
     * Same-tenant nesting is allowed; changing tenants within an active scope is rejected.
     * Always use try-with-resources and close scopes in reverse binding order.
     */
    public static Scope bind(TenantContext context) {
        Objects.requireNonNull(context, "Tenant context is required");
        Scope previous = activeScope();
        if (previous != null && !previous.context.tenantId().equals(context.tenantId())) {
            throw new IllegalStateException("Cannot replace an active tenant context");
        }
        Scope scope = new Scope(context, previous);
        ACTIVE.set(scope);
        return scope;
    }

    /** Returns the current thread's context, with no fallback. */
    public static Optional<TenantContext> current() {
        Scope scope = activeScope();
        return scope == null ? Optional.empty() : Optional.of(scope.context);
    }

    /** Fails closed if this thread has no active context. */
    public static TenantContext requireCurrent() {
        return current().orElseThrow(
                () -> new IllegalStateException("An active tenant context is required"));
    }

    private static Scope activeScope() {
        Scope scope = ACTIVE.get();
        if (scope == null) {
            // ThreadLocal.get() can install a null entry even without a binding.
            ACTIVE.remove();
        }
        return scope;
    }

    /**
     * An unforgeable lifecycle handle. Only its owning thread may close it.
     * An out-of-order close fails without changing state and may be retried later.
     * After successful close, repeated closes by its owner are harmless, including
     * while another context is active. There is deliberately no unrestricted clear API.
     */
    public static final class Scope implements AutoCloseable {

        private final TenantContext context;
        private final Scope previous;
        private final Thread owner;
        private boolean closed;

        private Scope(TenantContext context, Scope previous) {
            this.context = context;
            this.previous = previous;
            this.owner = Thread.currentThread();
        }

        @Override
        public void close() {
            // Check ownership before reading mutable thread-confined lifecycle state.
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Tenant scope must be closed by its owning thread");
            }
            if (closed) {
                return;
            }
            if (activeScope() != this) {
                throw new IllegalStateException("Tenant scopes must be closed in reverse binding order");
            }
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
            closed = true;
        }
    }
}
