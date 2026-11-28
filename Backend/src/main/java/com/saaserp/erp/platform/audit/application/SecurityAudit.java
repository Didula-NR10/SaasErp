package com.saaserp.erp.platform.audit.application;

public interface SecurityAudit {
    void record(SecurityAuditEvent event);
}
