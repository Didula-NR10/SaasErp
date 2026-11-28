package com.saaserp.erp.config;

import com.saaserp.erp.platform.audit.application.SecurityAudit;
import com.saaserp.erp.platform.audit.infrastructure.JdbcSecurityAudit;
import com.saaserp.erp.platform.authorization.application.AuthorizationRepository;
import com.saaserp.erp.platform.authorization.application.AuthorizationService;
import com.saaserp.erp.platform.authorization.application.CompanyRoleAssignmentRepository;
import com.saaserp.erp.platform.authorization.application.CompanyRoleAssignmentService;
import com.saaserp.erp.platform.authorization.application.DualApprovalRepository;
import com.saaserp.erp.platform.authorization.application.DualApprovalService;
import com.saaserp.erp.platform.authorization.application.PlatformAuthorizationRepository;
import com.saaserp.erp.platform.authorization.application.PlatformAuthorizationService;
import com.saaserp.erp.platform.authorization.application.StepUpAuthorizationService;
import com.saaserp.erp.platform.authorization.application.StepUpRepository;
import com.saaserp.erp.platform.authorization.infrastructure.JdbcAuthorizationRepository;
import com.saaserp.erp.platform.authorization.infrastructure.JdbcCompanyRoleAssignmentRepository;
import com.saaserp.erp.platform.authorization.infrastructure.JdbcDualApprovalRepository;
import com.saaserp.erp.platform.authorization.infrastructure.JdbcPlatformAuthorizationRepository;
import com.saaserp.erp.platform.authorization.infrastructure.JdbcStepUpRepository;
import com.saaserp.erp.platform.identity.application.ProtectionRepository;
import com.saaserp.erp.platform.identity.application.WebAuthnCeremonies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class AuthorizationConfig {
    @Bean SecurityAudit securityAudit(JdbcTemplate jdbc) { return new JdbcSecurityAudit(jdbc); }
    @Bean AuthorizationRepository authorizationRepository(JdbcTemplate jdbc) {
        return new JdbcAuthorizationRepository(jdbc);
    }
    @Bean AuthorizationService authorizationService(AuthorizationRepository repository, SecurityAudit audit) {
        return new AuthorizationService(repository, audit);
    }
    @Bean PlatformAuthorizationRepository platformAuthorizationRepository(JdbcTemplate jdbc) {
        return new JdbcPlatformAuthorizationRepository(jdbc);
    }
    @Bean PlatformAuthorizationService platformAuthorizationService(PlatformAuthorizationRepository repository,
            SecurityAudit audit) {
        return new PlatformAuthorizationService(repository, audit);
    }
    @Bean StepUpRepository stepUpRepository(JdbcTemplate jdbc) { return new JdbcStepUpRepository(jdbc); }
    @Bean StepUpAuthorizationService stepUpAuthorizationService(AuthorizationService authorization,
            StepUpRepository repository, ProtectionRepository protection, WebAuthnCeremonies webAuthn,
            SecurityAudit audit) {
        return new StepUpAuthorizationService(authorization, repository, protection, webAuthn, audit);
    }
    @Bean DualApprovalRepository dualApprovalRepository(JdbcTemplate jdbc) {
        return new JdbcDualApprovalRepository(jdbc);
    }
    @Bean DualApprovalService dualApprovalService(AuthorizationService authorization,
            DualApprovalRepository repository, SecurityAudit audit) {
        return new DualApprovalService(authorization, repository, audit);
    }
    @Bean CompanyRoleAssignmentRepository companyRoleAssignmentRepository(JdbcTemplate jdbc) {
        return new JdbcCompanyRoleAssignmentRepository(jdbc);
    }
    @Bean CompanyRoleAssignmentService companyRoleAssignmentService(AuthorizationService authorization,
            DualApprovalService dualApprovals, CompanyRoleAssignmentRepository repository, SecurityAudit audit) {
        return new CompanyRoleAssignmentService(authorization, dualApprovals, repository, audit);
    }
}
