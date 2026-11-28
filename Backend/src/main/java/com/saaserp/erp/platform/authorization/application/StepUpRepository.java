package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import java.util.UUID;

public interface StepUpRepository {
    UUID create(SessionGrant grant, OperationIntent intent, String digest, String options);
    String burnChallenge(SessionGrant grant, UUID authorizationId);
    boolean complete(SessionGrant grant, UUID authorizationId);
    boolean consume(SessionGrant grant, UUID authorizationId, OperationIntent intent, String digest);
}
