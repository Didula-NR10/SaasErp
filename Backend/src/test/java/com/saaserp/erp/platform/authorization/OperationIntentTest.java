package com.saaserp.erp.platform.authorization;

import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.authorization.domain.OperationIntent.Target;
import com.saaserp.erp.platform.authorization.domain.Permission;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperationIntentTest {
    @Test void digestIsCanonicalAndChangesWhenAnyTransactionDetailChanges() {
        var first = new OperationIntent(new Permission("erp.sales.refund"), Target.RESOURCE, "refund:42",
            Map.of("amount", "25.00", "currency", "USD"));
        var reordered = new OperationIntent(new Permission("erp.sales.refund"), Target.RESOURCE, "refund:42",
            Map.of("currency", "USD", "amount", "25.00"));
        var changed = new OperationIntent(new Permission("erp.sales.refund"), Target.RESOURCE, "refund:42",
            Map.of("amount", "25.01", "currency", "USD"));

        assertThat(first.digest()).isEqualTo(reordered.digest()).hasSize(64);
        assertThat(first.digest()).isNotEqualTo(changed.digest());
    }

    @Test void rejectsUnboundOrOversizedTransactionDetails() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationIntent(
            new Permission("erp.sales.refund"), Target.RESOURCE, "refund:42", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationIntent(
            new Permission("erp.sales.refund"), Target.RESOURCE, "refund:42",
            Map.of("details", "x".repeat(4097))));
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationIntent(
            new Permission("erp.sales.refund"), Target.RESOURCE, "invalid target", Map.of("amount", "25")));
    }
}
