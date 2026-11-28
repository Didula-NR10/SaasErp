package com.saaserp.erp.platform.authorization.application;

import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.authorization.domain.PlatformSessionContext;

public interface PlatformAuthorizationRepository {
    boolean permitsPlatform(PlatformSessionContext session, Permission permission);
}
