package com.saaserp.erp.shared.tenant.application;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.saaserp.erp.shared.tenant.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class TenantContextHolderTest {

    private static final TenantContext FIRST = new TenantContext(
            TenantId.parse("12345678-1234-abcd-9876-123456789abc"));
    private static final TenantContext SECOND = new TenantContext(
            TenantId.parse("22345678-1234-abcd-9876-123456789abc"));

    @BeforeEach
    void startsEmpty() {
        assertEmpty();
    }

    @AfterEach
    void endsEmptyWithoutForcedCleanup() {
        // Do not erase leaked state: a lifecycle bug must fail the test.
        assertEmpty();
    }

    @Test
    void currentIsEmptyWithoutBinding() {
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void requiredContextFailsClosedWhenAbsent() {
        assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
        assertEmpty();
    }

    @Test
    void bindsAndRetrievesContext() {
        try (var scope = TenantContextHolder.bind(FIRST)) {
            assertThat(TenantContextHolder.current()).contains(FIRST);
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        }
        assertEmpty();
    }

    @Test
    void rejectsNullBindingWithoutEstablishingContext() {
        assertThrows(NullPointerException.class, () -> TenantContextHolder.bind(null));
        assertEmpty();
    }

    @Test
    void rejectsNullBindingWithoutChangingActiveContext() {
        try (var scope = TenantContextHolder.bind(FIRST)) {
            assertThrows(NullPointerException.class, () -> TenantContextHolder.bind(null));
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        }
    }

    @Test
    void explicitlyClosesAndAllowsAnotherTenant() {
        var scope = TenantContextHolder.bind(FIRST);
        try {
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        } finally {
            scope.close();
        }
        assertEmpty();
        try (var next = TenantContextHolder.bind(SECOND)) {
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(SECOND);
        }
    }

    @Test
    void cleansUpAfterRuntimeException() {
        var failure = assertThrows(IllegalArgumentException.class, () -> {
            try (var scope = TenantContextHolder.bind(FIRST)) {
                throw new IllegalArgumentException("Operation failed");
            }
        });
        assertThat(failure.getMessage()).isEqualTo("Operation failed");
        assertEmpty();
    }

    @Test
    void cleansUpAfterCheckedException() {
        assertThrows(IOException.class, () -> {
            try (var scope = TenantContextHolder.bind(FIRST)) {
                throw new IOException("Operation failed");
            }
        });
        assertEmpty();
    }

    @Test
    void cleansUpAfterError() {
        assertThrows(AssertionError.class, () -> {
            try (var scope = TenantContextHolder.bind(FIRST)) {
                throw new AssertionError("Operation failed");
            }
        });
        assertEmpty();
    }

    @Test
    void sameTenantNestingRestoresExactOuterContext() {
        TenantContext equalContext = new TenantContext(TenantId.parse(FIRST.tenantId().toString()));
        try (var outer = TenantContextHolder.bind(FIRST)) {
            try (var inner = TenantContextHolder.bind(equalContext)) {
                assertThat(TenantContextHolder.requireCurrent()).isSameAs(equalContext);
            }
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        }
        assertEmpty();
    }

    @Test
    void rejectsDifferentTenantWithoutChangingOriginalContext() {
        try (var outer = TenantContextHolder.bind(FIRST)) {
            assertThrows(IllegalStateException.class, () -> TenantContextHolder.bind(SECOND));
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
            try (var inner = TenantContextHolder.bind(FIRST)) {
                assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
            }
        }
    }

    @Test
    void restoresOuterContextAfterNestedFailure() {
        try (var outer = TenantContextHolder.bind(FIRST)) {
            assertThrows(IOException.class, () -> {
                try (var inner = TenantContextHolder.bind(new TenantContext(FIRST.tenantId()))) {
                    throw new IOException("Nested operation failed");
                }
            });
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        }
    }

    @Test
    void rejectsOutOfOrderCloseWithoutCorruptingScopes() {
        try (var outer = TenantContextHolder.bind(FIRST)) {
            TenantContext innerContext = new TenantContext(FIRST.tenantId());
            try (var inner = TenantContextHolder.bind(innerContext)) {
                assertThrows(IllegalStateException.class, outer::close);
                assertThat(TenantContextHolder.requireCurrent()).isSameAs(innerContext);
            }
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        }
        assertEmpty();
    }

    @Test
    void repeatedCloseIsHarmless() {
        var scope = TenantContextHolder.bind(FIRST);
        scope.close();
        scope.close();
        assertEmpty();
    }

    @Test
    void staleClosedScopeCannotClearNewTenant() {
        var stale = TenantContextHolder.bind(FIRST);
        stale.close();
        try (var active = TenantContextHolder.bind(SECOND)) {
            stale.close();
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(SECOND);
        }
    }

    @Test
    void closedInnerScopeCannotClearOuterOrNewInnerScope() {
        try (var outer = TenantContextHolder.bind(FIRST)) {
            var stale = TenantContextHolder.bind(FIRST);
            stale.close();
            stale.close();
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
            TenantContext nextContext = new TenantContext(FIRST.tenantId());
            try (var next = TenantContextHolder.bind(nextContext)) {
                stale.close();
                assertThat(TenantContextHolder.requireCurrent()).isSameAs(nextContext);
            }
        }
    }

    @Test
    void foreignThreadCannotCloseScopeOrSeeOwnersContext() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var scope = TenantContextHolder.bind(FIRST)) {
            await(executor.submit(() -> {
                assertEmpty();
                assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
                assertThrows(IllegalStateException.class, scope::close);
                assertEmpty();
            }));
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void foreignClosePreservesForeignThreadsOwnContext() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var scope = TenantContextHolder.bind(FIRST)) {
            await(executor.submit(() -> {
                assertEmpty();
                try (var ownScope = TenantContextHolder.bind(SECOND)) {
                    assertThrows(IllegalStateException.class, scope::close);
                    assertThat(TenantContextHolder.requireCurrent()).isSameAs(SECOND);
                }
                assertEmpty();
            }));
            assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void foreignThreadCannotCloseEvenAnAlreadyClosedScope() throws Exception {
        var closed = TenantContextHolder.bind(FIRST);
        closed.close();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            await(executor.submit(() -> {
                assertThrows(IllegalStateException.class, closed::close);
                assertEmpty();
            }));
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void childThreadDoesNotInheritContext() throws Exception {
        // The executor creates its worker on submission, while the parent scope is active.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var scope = TenantContextHolder.bind(FIRST)) {
            await(executor.submit(() -> {
                assertEmpty();
                assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
            }));
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void simultaneousThreadsKeepIndependentContexts() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothBound = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Void> first = executor.submit(isolatedTask(FIRST, bothBound, release));
            Future<Void> second = executor.submit(isolatedTask(SECOND, bothBound, release));
            assertThat(bothBound.await(5, TimeUnit.SECONDS)).isTrue();
            assertEmpty();
            assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
            release.countDown();
            await(first);
            await(second);
        } finally {
            release.countDown();
            shutdown(executor);
        }
    }

    @Test
    void reusedExecutorThreadIsCleanAfterSuccessfulTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Thread firstWorker = await(executor.submit(() -> {
                assertEmpty();
                try (var scope = TenantContextHolder.bind(FIRST)) {
                    assertThat(TenantContextHolder.requireCurrent()).isSameAs(FIRST);
                }
                assertEmpty();
                return Thread.currentThread();
            }));
            Thread secondWorker = await(executor.submit(() -> {
                assertEmpty();
                assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
                try (var scope = TenantContextHolder.bind(SECOND)) {
                    assertThat(TenantContextHolder.requireCurrent()).isSameAs(SECOND);
                }
                assertEmpty();
                return Thread.currentThread();
            }));
            assertThat(secondWorker).isSameAs(firstWorker);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void reusedExecutorThreadIsCleanAfterFailedTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Thread worker = await(executor.submit((Callable<Thread>) Thread::currentThread));
            Future<Void> failed = executor.submit((Callable<Void>) () -> {
                assertEmpty();
                try (var scope = TenantContextHolder.bind(FIRST)) {
                    throw new IOException("Task failed");
                }
            });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> await(failed));
            assertThat(failure.getCause()).isInstanceOf(IOException.class);
            Thread reused = await(executor.submit(() -> {
                assertEmpty();
                assertThrows(IllegalStateException.class, TenantContextHolder::requireCurrent);
                try (var scope = TenantContextHolder.bind(SECOND)) {
                    assertThat(TenantContextHolder.requireCurrent()).isSameAs(SECOND);
                }
                assertEmpty();
                return Thread.currentThread();
            }));
            assertThat(reused).isSameAs(worker);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void repeatedLifecyclesRemainCleanOnReusedThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            await(executor.submit(() -> {
                for (int iteration = 0; iteration < 100; iteration++) {
                    assertEmpty();
                    TenantContext context = iteration % 2 == 0 ? FIRST : SECOND;
                    TenantContext other = iteration % 2 == 0 ? SECOND : FIRST;
                    try (var outer = TenantContextHolder.bind(context)) {
                        try (var inner = TenantContextHolder.bind(context)) {
                            assertThrows(IllegalStateException.class, () -> TenantContextHolder.bind(other));
                            assertThrows(IllegalStateException.class, outer::close);
                            assertThat(TenantContextHolder.requireCurrent()).isSameAs(context);
                        }
                        assertThat(TenantContextHolder.requireCurrent()).isSameAs(context);
                    }
                    assertEmpty();
                }
            }));
            await(executor.submit(TenantContextHolderTest::assertEmpty));
        } finally {
            shutdown(executor);
        }
    }

    private static Callable<Void> isolatedTask(
            TenantContext context, CountDownLatch bothBound, CountDownLatch release) {
        return () -> {
            assertEmpty();
            try (var scope = TenantContextHolder.bind(context)) {
                bothBound.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(TenantContextHolder.requireCurrent()).isSameAs(context);
            }
            assertEmpty();
            return null;
        };
    }

    private static void assertEmpty() {
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
