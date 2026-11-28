package com.saaserp.erp.platform.identity.application;

import java.util.UUID;

/** Transactional event contract; a queued event is not evidence of external email delivery. */
public interface SecurityNotifications {
    void enqueue(UUID account, UUID company, String kind);
}
