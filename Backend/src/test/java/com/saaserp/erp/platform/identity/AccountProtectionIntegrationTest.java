package com.saaserp.erp.platform.identity;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import com.saaserp.erp.platform.authorization.application.AuthorizationService;
import com.saaserp.erp.platform.authorization.application.CompanyRoleAssignmentService;
import com.saaserp.erp.platform.authorization.application.DualApprovalService;
import com.saaserp.erp.platform.authorization.application.PlatformAuthorizationService;
import com.saaserp.erp.platform.authorization.application.StepUpAuthorizationService;
import com.saaserp.erp.platform.authorization.domain.CompanyRole;
import com.saaserp.erp.platform.authorization.domain.OperationIntent;
import com.saaserp.erp.platform.authorization.domain.Permission;
import com.saaserp.erp.platform.authorization.domain.PlatformSessionContext;
import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Owner-run real PostgreSQL/Redis tests; migration fixtures use only the restricted test migrator. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AccountProtectionIntegrationTest {
    private static final String RUN=UUID.randomUUID().toString().replace("-","");
    private static final String HOST="mfa-"+RUN+".test",OTHER="other-"+RUN+".test",NS="hich:test:mfa:"+RUN;
    private static final String PASSWORD="an original long test passphrase",NEW_PASSWORD="a different long test passphrase";
    private static final String KEY=testKey();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("app.security.origins",()->"https://"+HOST+",https://"+OTHER);
        registry.add("app.security.session-namespace",()->NS);
        registry.add("app.security.protection.active-key",()->"test");registry.add("app.security.protection.keys",()->"test:"+KEY);
        registry.add("app.security.password-blocklist",AccountProtectionIntegrationTest::blocklist);
    }
    @Autowired AccountProtectionService service;@Autowired ProtectionRepository store;@Autowired IdentityRepository identities;
    @Autowired AuthorizationService authorization;
    @Autowired CompanyRoleAssignmentService roleAssignments;
    @Autowired PlatformAuthorizationService platformAuthorization;
    @Autowired ApiRequestQuota apiRequestQuota;
    @Autowired StepUpAuthorizationService stepUp;
    @Autowired DualApprovalService dualApprovals;
    @Autowired WebAuthnCeremonies ceremonies;@Autowired PasswordVerification verification;@Autowired PasswordPolicy passwordPolicy;
    @Autowired FactorCryptography crypto;@Autowired PasswordEncoder passwords;@Autowired JdbcTemplate runtime;
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired SessionRepository<? extends Session> sessions;
    @Autowired StringRedisTemplate redis;
    @Value("${app.datasource.migration.url}")String url;@Value("${app.datasource.migration.username}")String user;@Value("${app.datasource.migration.password}")String migrationPassword;
    private JdbcTemplate fixtures;private UUID account,company,otherCompany,secondaryAccount,roleTargetAccount;
    private String email,binding,source,secondaryEmail,secondaryBinding;
    private final Set<String> ownedSessions=new HashSet<>(),scopes=new HashSet<>();
    @BeforeEach void seed(){
        assertThat(runtime.queryForObject("SELECT current_database()",String.class)).isEqualTo("hich_erp_test");
        assertThat(runtime.queryForObject("SELECT current_user",String.class)).isEqualTo("hich_runtime_test");assertThat(user).isEqualTo("hich_migrator_test");
        fixtures=new JdbcTemplate(new DriverManagerDataSource(url,user,migrationPassword));
        assertThat(fixtures.queryForObject("SELECT current_database()",String.class)).isEqualTo("hich_erp_test");
        account=UUID.randomUUID();company=UUID.randomUUID();otherCompany=UUID.randomUUID();email=account+"@example.test";binding=crypto.randomToken();source="mfa-source-"+account;
        fixtures.update("INSERT INTO platform.accounts(id,normalized_email,password_hash,status,email_verified,mfa_required) VALUES(?,?,?,'ACTIVE',true,true)",account,email,passwords.encode(PASSWORD));
        fixtures.update("INSERT INTO platform.auth_companies(id,hostname,status,entitled,mfa_required,totp_allowed) VALUES(?,?,'ACTIVE',true,true,true),(?,?,'ACTIVE',true,true,true)",company,HOST,otherCompany,OTHER);
        fixtures.update("INSERT INTO platform.company_memberships(account_id,company_id,active,mfa_required,totp_allowed,mfa_enrollment_until) VALUES(?,?,true,true,true,CURRENT_TIMESTAMP+INTERVAL '15 minutes'),(?,?,true,true,true,CURRENT_TIMESTAMP+INTERVAL '15 minutes')",account,company,account,otherCompany);
    }
    @AfterEach void cleanup(){
        try{
            for(String id:ownedSessions)sessions.deleteById(id);
            if(fixtures!=null && account!=null){
                for(String scope:scopes)redis.delete(NS+":protection:limits:account:"+crypto.digest(scope));
                redis.delete(NS+":protection:limits:source:"+crypto.digest(source));redis.delete(NS+":limits:source:"+crypto.digest(source));redis.delete(NS+":limits:account:"+crypto.digest(email));
                redis.delete(NS+":requests:limits:api-source:"+crypto.digest("api-source-"+account));
                redis.delete(NS+":requests:limits:api-account:"+crypto.digest(company+":"+account));
                fixtures.update("DELETE FROM platform.platform_administrator_sessions WHERE account_id=?",account);
                if(fixtures.queryForObject("SELECT EXISTS(SELECT 1 FROM platform.platform_account_roles WHERE account_id=?)",Boolean.class,account))
                    executeAsActor(account,"DELETE FROM platform.platform_account_roles WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.dual_approvals WHERE company_id IN (?,?)",company,otherCompany);
                fixtures.update("DELETE FROM platform.step_up_authorizations WHERE account_id=?",account);
                if(secondaryAccount!=null)fixtures.update("DELETE FROM platform.step_up_authorizations WHERE account_id=?",secondaryAccount);
                fixtures.update("DELETE FROM platform.mfa_challenges WHERE transaction_id IN(SELECT id FROM platform.mfa_transactions WHERE account_id=?)",account);
                fixtures.update("DELETE FROM platform.authentication_sessions WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.webauthn_credentials WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.account_totp WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.recovery_codes WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.account_tokens WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.security_notifications WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.mfa_transactions WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.company_memberships WHERE account_id=?",account);
                fixtures.update("DELETE FROM platform.accounts WHERE id=?",account);
                if(roleTargetAccount!=null) {
                    fixtures.update("DELETE FROM platform.company_memberships WHERE account_id=?",roleTargetAccount);
                    fixtures.update("DELETE FROM platform.accounts WHERE id=?",roleTargetAccount);
                }
                if(secondaryAccount!=null) {
                    fixtures.update("DELETE FROM platform.mfa_challenges WHERE transaction_id IN(SELECT id FROM platform.mfa_transactions WHERE account_id=?)",secondaryAccount);
                    fixtures.update("DELETE FROM platform.authentication_sessions WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.webauthn_credentials WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.account_totp WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.recovery_codes WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.account_tokens WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.security_notifications WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.mfa_transactions WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.company_memberships WHERE account_id=?",secondaryAccount);
                    fixtures.update("DELETE FROM platform.accounts WHERE id=?",secondaryAccount);
                }
                fixtures.update("DELETE FROM platform.auth_companies WHERE id IN (?,?)",company,otherCompany);
            }
        }finally{assertThat(TenantContextHolder.current()).isEmpty();}
    }
    @Test void nativeRegistrationCreatesMfaAuthorityOnlyAfterValidUvAndCompletion() throws Exception{
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();enroll(transaction,device);
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.authentication_sessions WHERE account_id=?",Long.class,account)).isZero();
        SessionGrant grant=service.finish(transaction,binding,HOST);assertThat(grant.assurance()).isEqualTo("WEBAUTHN");assertThat(identities.isCurrent(grant)).isTrue();
        assertThatThrownBy(()->service.finish(transaction,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void postgresAuthorizationReadsCurrentCompanyRoleAndRevokesStaleSessionPermissions() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();enroll(transaction,device);
        SessionGrant grant=service.finish(transaction,binding,HOST);
        var refund=new Permission("erp.sales.refund");

        assertThat(authorization.permitsCompany(grant,company,refund)).isTrue();
        assertThat(authorization.permitsCompany(grant,otherCompany,refund)).isFalse();

        fixtures.update("UPDATE platform.company_memberships SET auth_role='CASHIER' WHERE account_id=? AND company_id=?",account,company);
        assertThat(authorization.permitsCompany(grant,company,refund)).isFalse();
    }

    @Test void platformAuthorizationRequiresCurrentVerifiedHardwareSessionAndNeverUsesCompanyAuthority() throws Exception {
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();enroll(transaction,device);
        SessionGrant companyGrant=service.finish(transaction,binding,HOST);
        fixtures.update("UPDATE platform.accounts SET privileged=true,mfa_required=true WHERE id=?",account);
        executeAsActor(account,"""
            INSERT INTO platform.platform_account_roles(account_id,role_code,active,grant_version,granted_by)
            VALUES(?,'PLATFORM_ADMIN',true,1,?)
            """,account,account);
        UUID platformSession=UUID.randomUUID();
        String sessionDigest="a".repeat(64),credentialDigest="b".repeat(64),attestationDigest="c".repeat(64);
        fixtures.update("""
            INSERT INTO platform.platform_administrator_sessions(
                id,account_id,session_binding_digest,hostname,account_version,hardware_assurance,
                credential_id_digest,attestation_evidence_digest,attestation_verified_at,created_at,
                last_access_at,expires_at)
            VALUES(?,?,?,'admin.hich.com',
                (SELECT security_version FROM platform.accounts WHERE id=?),'HARDWARE_ATTESTED',
                ?,?,clock_timestamp(),clock_timestamp(),clock_timestamp(),clock_timestamp()+INTERVAL '8 hours')
            """,platformSession,account,sessionDigest,account,credentialDigest,attestationDigest);

        assertThat(platformAuthorization.permits(new PlatformSessionContext(
            companyGrant.id(),account,sessionDigest,"admin.hich.com"),new Permission("platform.accounts.read"))).isFalse();
        var platformGrant=new PlatformSessionContext(platformSession,account,sessionDigest,"admin.hich.com");
        assertThat(platformAuthorization.permits(platformGrant,new Permission("platform.accounts.read"))).isTrue();
        assertThat(platformAuthorization.permits(platformGrant,new Permission("platform.roles.manage"))).isFalse();
        assertThat(platformAuthorization.permits(new PlatformSessionContext(
            platformSession,account,sessionDigest,HOST),new Permission("platform.accounts.read"))).isFalse();

        executeAsActor(account,"UPDATE platform.platform_account_roles SET active=false WHERE account_id=?",account);
        assertThat(platformAuthorization.permits(platformGrant,new Permission("platform.accounts.read"))).isFalse();
    }

    @Test void companyRoleAssignmentRequiresBoundDualApprovalAndFencesOnlyTheSelectedMembership() throws Exception {
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        roleTargetAccount=UUID.randomUUID();
        fixtures.update("""
            INSERT INTO platform.accounts(id,normalized_email,password_hash,status,email_verified,mfa_required)
            VALUES(?,?,?,'ACTIVE',true,true)
            """,roleTargetAccount,roleTargetAccount+"@example.test",passwords.encode(PASSWORD));
        fixtures.update("""
            INSERT INTO platform.company_memberships(account_id,company_id,active,auth_role,mfa_required)
            VALUES(?,?,true,'EMPLOYEE',true),(?,?,true,'EMPLOYEE',true)
            """,roleTargetAccount,company,roleTargetAccount,otherCompany);

        var requesterDevice=new WebAuthnTestAuthenticator();
        SessionGrant requester=registerOwner(account,email,binding,requesterDevice);
        var approverOwner=secondaryOwner();
        var targetIsApprover=new OperationIntent(new Permission("company.roles.manage"),
            OperationIntent.Target.MEMBERSHIP,secondaryAccount.toString(),
            Map.of("companyId",company.toString(),"currentRole","OWNER","requestedRole","ADMIN"));
        UUID targetStepUp=proveStepUp(requester,targetIsApprover,requesterDevice);
        UUID targetApproval=roleAssignments.request(requester,secondaryAccount,CompanyRole.ADMIN,
            "role-approver-target-idempotency",targetStepUp);
        assertThatThrownBy(()->roleAssignments.approve(approverOwner.grant(),targetApproval,
            secondaryAccount,CompanyRole.ADMIN,UUID.randomUUID()))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThat(fixtures.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",
            String.class,targetApproval)).isEqualTo("REQUESTED");

        long versionBefore=fixtures.queryForObject("""
            SELECT security_version FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,Long.class,roleTargetAccount,company);

        assertThatThrownBy(()->roleAssignments.request(requester,account,CompanyRole.ADMIN,
            "role-self-escalation-idempotency",UUID.randomUUID()))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThatThrownBy(()->roleAssignments.request(requester,UUID.randomUUID(),CompanyRole.MANAGER,
            "role-cross-company-idempotency",UUID.randomUUID()))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThatThrownBy(()->roleAssignments.assign(requester,UUID.randomUUID(),
            roleTargetAccount,CompanyRole.MANAGER))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);

        var intent=new OperationIntent(new Permission("company.roles.manage"),
            OperationIntent.Target.MEMBERSHIP,roleTargetAccount.toString(),
            Map.of("companyId",company.toString(),"currentRole","EMPLOYEE","requestedRole","MANAGER"));
        UUID requesterStepUp=proveStepUp(requester,intent,requesterDevice);
        UUID approval=roleAssignments.request(requester,roleTargetAccount,CompanyRole.MANAGER,
            "role-assignment-idempotency-0001",requesterStepUp);
        UUID approverStepUp=proveStepUp(approverOwner.grant(),intent,approverOwner.device());
        assertThat(roleAssignments.approve(approverOwner.grant(),approval,roleTargetAccount,
            CompanyRole.MANAGER,approverStepUp)).isTrue();
        assertThatThrownBy(()->roleAssignments.assign(requester,approval,roleTargetAccount,CompanyRole.ADMIN))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,company)).isEqualTo("EMPLOYEE");

        roleAssignments.assign(requester,approval,roleTargetAccount,CompanyRole.MANAGER);
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,company)).isEqualTo("MANAGER");
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,otherCompany)).isEqualTo("EMPLOYEE");
        assertThat(fixtures.queryForObject("""
            SELECT security_version FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,Long.class,roleTargetAccount,company)).isEqualTo(versionBefore+1);
        assertThat(fixtures.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",
            String.class,approval)).isEqualTo("CONSUMED");
    }

    @Test void postgresRoleDigestMatchesJavaCanonicalEncoding() {
        UUID fixedCompany=UUID.fromString("12345678-1234-5678-9abc-123456789abc");
        UUID fixedTarget=UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
        String golden="2f8a8afbbddb2397c70ce718486bdb4542630780fda8dee0c8d563ad69db06fc";
        assertThat(roleIntent(fixedCompany,fixedTarget,"EMPLOYEE","MANAGER").digest()).isEqualTo(golden);
        assertThat(fixtures.queryForObject(
            "SELECT platform.company_role_operation_digest(?,?,?,?)",String.class,
            fixedCompany,fixedTarget,"EMPLOYEE","MANAGER")).isEqualTo(golden);
        for(var oldRole:CompanyRole.values()) for(var newRole:CompanyRole.values()) {
            var intent=roleIntent(fixedCompany,fixedTarget,oldRole.name(),newRole.name());
            assertThat(fixtures.queryForObject(
                "SELECT platform.company_role_operation_digest(?,?,?,?)",String.class,
                fixedCompany,fixedTarget,oldRole.name(),newRole.name())).isEqualTo(intent.digest());
        }
        // Exercise byte lengths, not character counts, and delimiter-like data in the encoder.
        var unicode=roleIntent(fixedCompany,fixedTarget,"é:所有者","𐐀\nmanager");
        assertThat(fixtures.queryForObject(
            "SELECT platform.company_role_operation_digest(?,?,?,?)",String.class,
            fixedCompany,fixedTarget,"é:所有者","𐐀\nmanager")).isEqualTo(unicode.digest());
    }

    @Test void restrictedSqlCannotChangeRoleIntentUsingTheOriginalApprovedDigest() throws Exception {
        var approved=approvedRoleChange();
        Object[] valid=assignmentArguments(approved,UUID.randomUUID());
        long version=targetVersion();
        long auditCount=companyAuditCount();
        // Alter each operation component while retaining the original valid approval digest.
        for(int index:new int[]{2,4,5,6,8}) {
            Object[] attack=valid.clone();
            attack[index]=switch(index) {
                case 2 -> otherCompany;
                case 4 -> secondaryAccount;
                case 5 -> "CASHIER";
                case 6 -> "OWNER";
                default -> roleIntent(company,roleTargetAccount,"EMPLOYEE","OWNER").digest();
            };
            assertThat(assignDirect(attack)).as("changed argument %s",index).isFalse();
        }
        // Supply a matching *new* digest as well: it must still match the stored approval.
        Object[] changed=valid.clone();
        changed[6]="OWNER";
        changed[8]=roleIntent(company,roleTargetAccount,"EMPLOYEE","OWNER").digest();
        assertThat(assignDirect(changed)).isFalse();
        for(int index=0;index<valid.length;index++) {
            Object[] attack=valid.clone();attack[index]=null;
            assertThat(assignDirect(attack)).as("NULL argument %s",index).isFalse();
        }
        assertRoleApprovalUnchanged(approved,version,auditCount);

        UUID correlation=(UUID)valid[9];
        assertThat(assignDirect(valid)).isTrue();
        assertThat(assignDirect(valid)).isFalse();
        assertThat(targetVersion()).isEqualTo(version+1);
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,company)).isEqualTo("MANAGER");
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,otherCompany)).isEqualTo("EMPLOYEE");
        assertThat(runtime.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",
            String.class,approved.id())).isEqualTo("CONSUMED");
        assertThat(fixtures.queryForList("""
            SELECT action FROM platform.security_audit_events
            WHERE company_id=? AND correlation_id=? ORDER BY action
            """,String.class,company,correlation)).containsExactly("DUAL_APPROVAL_CONSUMED","ROLE_CHANGED");
        assertThat(fixtures.queryForObject("""
            SELECT count(*) FROM platform.security_audit_events
            WHERE target_id=? AND action='DUAL_APPROVAL_CONSUMED'
            """,Long.class,approved.id().toString())).isEqualTo(1L);
    }

    @Test void restrictedSqlRoleAssignmentExpiresThroughCorrelatedCurrentConsumer() throws Exception {
        var approved=approvedRoleChange();
        fixtures.update("UPDATE platform.dual_approvals SET expires_at=clock_timestamp()-INTERVAL '1 second' WHERE id=?",approved.id());
        UUID correlation=UUID.randomUUID();
        Object[] args=assignmentArguments(approved,correlation);
        long version=targetVersion();
        assertThat(assignDirect(args)).isFalse();
        assertThat(assignDirect(args)).isFalse();
        assertThat(targetVersion()).isEqualTo(version);
        assertThat(runtime.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",
            String.class,approved.id())).isEqualTo("EXPIRED");
        assertThat(fixtures.queryForObject("SELECT consumed_at IS NULL FROM platform.dual_approvals WHERE id=?",
            Boolean.class,approved.id())).isTrue();
        assertThat(fixtures.queryForList("""
            SELECT correlation_id FROM platform.security_audit_events
            WHERE target_id=? AND action='DUAL_APPROVAL_EXPIRED'
            """,UUID.class,approved.id().toString())).containsExactly(correlation);
        assertThat(fixtures.queryForObject("""
            SELECT count(*) FROM platform.security_audit_events WHERE company_id=?
            AND action IN ('ROLE_CHANGED','DUAL_APPROVAL_CONSUMED')
            """,Long.class,company)).isZero();
    }

    @Test void concurrentRestrictedSqlRoleAssignmentSucceedsExactlyOnce() throws Exception {
        var approved=approvedRoleChange();
        long version=targetVersion();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try {
            var one=executor.submit(()->{ready.countDown();start.await();return assignDirect(assignmentArguments(approved,first));});
            var two=executor.submit(()->{ready.countDown();start.await();return assignDirect(assignmentArguments(approved,second));});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            boolean oneResult=one.get(20,TimeUnit.SECONDS),twoResult=two.get(20,TimeUnit.SECONDS);
            assertThat(List.of(oneResult,twoResult)).containsExactlyInAnyOrder(true,false);
            UUID winner=oneResult?first:second;
            assertThat(fixtures.queryForList("""
                SELECT correlation_id FROM platform.security_audit_events WHERE company_id=?
                AND action IN ('ROLE_CHANGED','DUAL_APPROVAL_CONSUMED')
                """,UUID.class,company)).containsExactlyInAnyOrder(winner,winner);
            assertThat(targetVersion()).isEqualTo(version+1);
        } finally { start.countDown();executor.shutdownNow(); }
    }

    @Test void restrictedSqlRoleMutationApprovalAndAuditRollBackTogether() throws Exception {
        var approved=approvedRoleChange();
        long version=targetVersion(),auditCount=companyAuditCount();
        Object[] args=assignmentArguments(approved,UUID.randomUUID());
        try(var connection=runtime.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT platform.assign_company_role(?,?,?,?,?,?,?,?,?,?)")) {
                for(int index=0;index<args.length;index++) statement.setObject(index+1,args[index]);
                try(var rows=statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();assertThat(rows.getBoolean(1)).isTrue();
                }
            } finally { connection.rollback(); }
        }
        assertRoleApprovalUnchanged(approved,version,auditCount);
        assertThat(assignDirect(args)).isTrue();
        assertThat(assignDirect(args)).isFalse();
    }

    @Test void restrictedSqlRevalidatesApproverAuthorityBeforeRoleMutation() throws Exception {
        var approved=approvedRoleChange();
        fixtures.update("UPDATE platform.company_memberships SET active=false WHERE account_id=? AND company_id=?",
            secondaryAccount,company);
        long version=targetVersion(),auditCount=companyAuditCount();
        assertThat(assignDirect(assignmentArguments(approved,UUID.randomUUID()))).isFalse();
        assertRoleApprovalUnchanged(approved,version,auditCount);
    }

    @Test void restrictedSqlWorkflowNullBindingsNeverConsumeOrCancelApproval() throws Exception {
        var approved=approvedRoleChange();
        var requester=approved.requester();var approver=approved.approver();
        UUID correlation=UUID.randomUUID();
        Object[] consume={approved.id(),requester.id(),account,company,HOST,
            "company.roles.manage","MEMBERSHIP",roleTargetAccount.toString(),approved.intent().digest(),correlation};
        Object[] approve={approved.id(),approver.id(),secondaryAccount,company,HOST,
            approved.intent().digest(),approved.approverStepUp(),correlation};
        Object[] cancel={approved.id(),requester.id(),account,company,HOST,correlation};
        Object[] request={UUID.randomUUID(),company,HOST,account,requester.id(),"company.roles.manage",
            "MEMBERSHIP",roleTargetAccount.toString(),approved.intent().digest(),approved.idempotencyDigest(),
            approved.requesterStepUp(),correlation};
        long version=targetVersion(),auditCount=companyAuditCount();
        assertNullArgumentsDenied("SELECT platform.consume_dual_approval(?,?,?,?,?,?,?,?,?,?)",consume,Boolean.class,false);
        assertNullArgumentsDenied("SELECT platform.approve_dual_approval(?,?,?,?,?,?,?,?)",approve,Boolean.class,false);
        assertNullArgumentsDenied("SELECT platform.cancel_dual_approval(?,?,?,?,?,?)",cancel,Boolean.class,false);
        assertNullArgumentsDenied("SELECT platform.create_dual_approval(?,?,?,?,?,?,?,?,?,?,?,?)",request,UUID.class,null);
        assertNullArgumentsDenied("SELECT platform.expire_dual_approval(?,?)",
            new Object[]{approved.id(),correlation},Boolean.class,false);
        // Non-null but wrong operation bindings must also fail at the generic consumer.
        for(int index:new int[]{3,4,5,6,7,8}) {
            Object[] attack=consume.clone();
            attack[index]=index==3?otherCompany:"wrong";
            assertThat(runtime.queryForObject("SELECT platform.consume_dual_approval(?,?,?,?,?,?,?,?,?,?)",
                Boolean.class,attack)).isFalse();
        }
        assertRoleApprovalUnchanged(approved,version,auditCount);
        assertThat(assignDirect(assignmentArguments(approved,correlation))).isTrue();
    }

    private <T> void assertNullArgumentsDenied(String sql,Object[] valid,Class<T> type,T expected) {
        for(int index=0;index<valid.length;index++) {
            Object[] attack=valid.clone();attack[index]=null;
            assertThat(runtime.queryForObject(sql,type,attack)).as("%s NULL argument %s",sql,index).isEqualTo(expected);
        }
    }

    private record ApprovedRoleChange(UUID id,SessionGrant requester,SessionGrant approver,
        OperationIntent intent,UUID requesterStepUp,UUID approverStepUp,String idempotencyDigest) { }

    private ApprovedRoleChange approvedRoleChange() throws Exception {
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        roleTargetAccount=UUID.randomUUID();
        fixtures.update("""
            INSERT INTO platform.accounts(id,normalized_email,password_hash,status,email_verified,mfa_required)
            VALUES(?,?,?,'ACTIVE',true,true)
            """,roleTargetAccount,roleTargetAccount+"@example.test",passwords.encode(PASSWORD));
        fixtures.update("""
            INSERT INTO platform.company_memberships(account_id,company_id,active,auth_role,mfa_required)
            VALUES(?,?,true,'EMPLOYEE',true),(?,?,true,'EMPLOYEE',true)
            """,roleTargetAccount,company,roleTargetAccount,otherCompany);
        var device=new WebAuthnTestAuthenticator();
        var requester=registerOwner(account,email,binding,device);
        var approver=secondaryOwner();
        var intent=roleIntent(company,roleTargetAccount,"EMPLOYEE","MANAGER");
        UUID requesterStepUp=proveStepUp(requester,intent,device);
        UUID id=roleAssignments.request(requester,roleTargetAccount,CompanyRole.MANAGER,
            "restricted-sql-role-approval-0001",requesterStepUp);
        UUID approverStepUp=proveStepUp(approver.grant(),intent,approver.device());
        assertThat(roleAssignments.approve(approver.grant(),id,roleTargetAccount,
            CompanyRole.MANAGER,approverStepUp)).isTrue();
        String idempotency=runtime.queryForObject("SELECT idempotency_digest FROM platform.dual_approvals WHERE id=?",String.class,id);
        return new ApprovedRoleChange(id,requester,approver.grant(),intent,requesterStepUp,approverStepUp,idempotency);
    }

    private static OperationIntent roleIntent(UUID companyId,UUID target,String expected,String requested) {
        return new OperationIntent(new Permission("company.roles.manage"),OperationIntent.Target.MEMBERSHIP,
            target.toString(),Map.of("companyId",companyId.toString(),"currentRole",expected,"requestedRole",requested));
    }

    private Object[] assignmentArguments(ApprovedRoleChange approved,UUID correlation) {
        return new Object[]{approved.requester().id(),account,company,HOST,roleTargetAccount,
            "EMPLOYEE","MANAGER",approved.id(),approved.intent().digest(),correlation};
    }

    private boolean assignDirect(Object[] args) {
        return Boolean.TRUE.equals(runtime.queryForObject(
            "SELECT platform.assign_company_role(?,?,?,?,?,?,?,?,?,?)",Boolean.class,args));
    }

    private long targetVersion() {
        return fixtures.queryForObject("""
            SELECT security_version FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,Long.class,roleTargetAccount,company);
    }

    private long companyAuditCount() {
        return fixtures.queryForObject("SELECT count(*) FROM platform.security_audit_events WHERE company_id=?",Long.class,company);
    }

    private void assertRoleApprovalUnchanged(ApprovedRoleChange approved,long version,long auditCount) {
        assertThat(targetVersion()).isEqualTo(version);
        assertThat(fixtures.queryForObject("""
            SELECT auth_role FROM platform.company_memberships WHERE account_id=? AND company_id=?
            """,String.class,roleTargetAccount,company)).isEqualTo("EMPLOYEE");
        assertThat(runtime.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",String.class,approved.id()))
            .isEqualTo("APPROVED");
        assertThat(fixtures.queryForObject("SELECT consumed_at IS NULL FROM platform.dual_approvals WHERE id=?",
            Boolean.class,approved.id())).isTrue();
        assertThat(companyAuditCount()).isEqualTo(auditCount);
    }

    @Test void applicationApiQuotaUsesRedisAndSeparatesSourceAndAuthenticatedAccountBudgets() {
        String sourceKey="api-source-"+account;
        for(int i=0;i<300;i++) assertThat(apiRequestQuota.allow(sourceKey,null)).isTrue();
        assertThat(apiRequestQuota.allow(sourceKey,null)).isFalse();

        String accountSource="api-account-source-"+account;
        String accountScope=company+":"+account;
        for(int i=0;i<120;i++) assertThat(apiRequestQuota.allow(accountSource,accountScope)).isTrue();
        assertThat(apiRequestQuota.allow(accountSource,accountScope)).isFalse();
    }
    @Test void nativeWebAuthnStepUpBindsOperationDetailsAndIsSingleUse() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();enroll(transaction,device);
        SessionGrant grant=service.finish(transaction,binding,HOST);
        var intent=new OperationIntent(new Permission("erp.sales.refund"),OperationIntent.Target.RESOURCE,
            "refund:42",Map.of("amount","25.00","currency","USD"));
        var challenge=stepUp.begin(grant,intent);
        stepUp.verify(grant,challenge.id(),device.assertion(challenge.publicKey(),HOST,"https://"+HOST,true,device.nextCounter()));

        var modified=new OperationIntent(intent.permission(),intent.target(),intent.targetId(),
            Map.of("amount","25.01","currency","USD"));
        assertThatThrownBy(()->stepUp.consume(grant,challenge.id(),modified)).isExactlyInstanceOf(ProtectionFailure.class);
        stepUp.consume(grant,challenge.id(),intent);
        assertThatThrownBy(()->stepUp.consume(grant,challenge.id(),intent)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(fixtures.queryForObject("SELECT consumed_at IS NOT NULL FROM platform.step_up_authorizations WHERE id=?",
            Boolean.class,challenge.id())).isTrue();

        var expiring=stepUp.begin(grant,intent);
        stepUp.verify(grant,expiring.id(),
            device.assertion(expiring.publicKey(),HOST,"https://"+HOST,true,device.nextCounter()));
        fixtures.update("UPDATE platform.step_up_authorizations SET expires_at=clock_timestamp()-INTERVAL '1 second' WHERE id=?",
            expiring.id());
        assertThatThrownBy(()->stepUp.consume(grant,expiring.id(),intent)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void dualApprovalRequiresDistinctNativeWebAuthnIdentitiesAndExactDetails() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        var requesterDevice=new WebAuthnTestAuthenticator();
        SessionGrant requester=registerOwner(account,email,binding,requesterDevice);
        var approverOwner=secondaryOwner();
        var approverDevice=approverOwner.device();
        SessionGrant approver=approverOwner.grant();
        var intent=new OperationIntent(new Permission("erp.sales.refund"),OperationIntent.Target.RESOURCE,
            "refund:dual-1",Map.of("amount","25.00","currency","USD"));

        var requesterStepUp=proveStepUp(requester,intent,requesterDevice);
        UUID approval=dualApprovals.request(requester,intent,"dual-approval-idempotency-1",requesterStepUp);
        assertThatThrownBy(()->dualApprovals.approve(requester,approval,intent,requesterStepUp))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);

        var approverStepUp=proveStepUp(approver,intent,approverDevice);
        assertThat(dualApprovals.approve(approver,approval,intent,approverStepUp)).isTrue();
        var modified=new OperationIntent(intent.permission(),intent.target(),intent.targetId(),
            Map.of("amount","25.01","currency","USD"));
        assertThatThrownBy(()->dualApprovals.consume(requester,approval,modified))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        dualApprovals.consume(requester,approval,intent);
        assertThatThrownBy(()->dualApprovals.consume(requester,approval,intent))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThat(fixtures.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",String.class,approval))
            .isEqualTo("CONSUMED");

        var expiringIntent=new OperationIntent(intent.permission(),intent.target(),"refund:dual-expired",
            Map.of("amount","27.00","currency","USD"));
        UUID expiringRequestStepUp=proveStepUp(requester,expiringIntent,requesterDevice);
        UUID expiringApproval=dualApprovals.request(requester,expiringIntent,
            "dual-approval-expiring-idempotency",expiringRequestStepUp);
        UUID expiringApproverStepUp=proveStepUp(approver,expiringIntent,approverDevice);
        dualApprovals.approve(approver,expiringApproval,expiringIntent,expiringApproverStepUp);
        fixtures.update("UPDATE platform.dual_approvals SET expires_at=clock_timestamp()-INTERVAL '1 second' WHERE id=?",
            expiringApproval);
        assertThatThrownBy(()->dualApprovals.consume(requester,expiringApproval,expiringIntent))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
        assertThat(fixtures.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",
            String.class,expiringApproval)).isEqualTo("EXPIRED");
    }

    @Test void concurrentApprovalAndCancellationResolveThroughLockedStateTransitions() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='OWNER' WHERE account_id=? AND company_id=?",account,company);
        var requesterDevice=new WebAuthnTestAuthenticator();
        SessionGrant requester=registerOwner(account,email,binding,requesterDevice);
        var approverOwner=secondaryOwner();
        var intent=new OperationIntent(new Permission("erp.sales.refund"),OperationIntent.Target.RESOURCE,
            "refund:race-1",Map.of("amount","30.00","currency","USD"));
        UUID requesterStepUp=proveStepUp(requester,intent,requesterDevice);
        UUID approval=dualApprovals.request(requester,intent,"dual-approval-race-idempotency",requesterStepUp);
        UUID approverStepUp=proveStepUp(approverOwner.grant(),intent,approverOwner.device());

        var ready=new CountDownLatch(2);
        var start=new CountDownLatch(1);
        var workers=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> approved=workers.submit(()->{
                ready.countDown();start.await();
                try { return dualApprovals.approve(approverOwner.grant(),approval,intent,approverStepUp); }
                catch(AuthorizationService.AuthorizationDenied denied) { return false; }
            });
            Future<Boolean> cancelled=workers.submit(()->{
                ready.countDown();start.await();dualApprovals.cancel(requester,approval);return true;
            });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            approved.get(10,TimeUnit.SECONDS);
            assertThat(cancelled.get(10,TimeUnit.SECONDS)).isTrue();
        } finally {
            workers.shutdownNow();
        }
        assertThat(fixtures.queryForObject("SELECT status FROM platform.dual_approvals WHERE id=?",String.class,approval))
            .isEqualTo("CANCELLED");
        assertThatThrownBy(()->dualApprovals.consume(requester,approval,intent))
            .isInstanceOf(AuthorizationService.AuthorizationDenied.class);
    }

    private record RegisteredOwner(SessionGrant grant,WebAuthnTestAuthenticator device) { }

    private RegisteredOwner secondaryOwner()throws Exception {
        secondaryAccount=UUID.randomUUID();secondaryEmail=secondaryAccount+"@example.test";secondaryBinding=crypto.randomToken();
        fixtures.update("INSERT INTO platform.accounts(id,normalized_email,password_hash,status,email_verified,mfa_required) VALUES(?,?,?,'ACTIVE',true,true)",
            secondaryAccount,secondaryEmail,passwords.encode(PASSWORD));
        fixtures.update("""
            INSERT INTO platform.company_memberships(account_id,company_id,active,auth_role,mfa_required,
                totp_allowed,mfa_enrollment_until)
            VALUES(?,?,true,'OWNER',true,true,CURRENT_TIMESTAMP+INTERVAL '15 minutes')
            """,secondaryAccount,company);
        var device=new WebAuthnTestAuthenticator();
        return new RegisteredOwner(registerOwner(secondaryAccount,secondaryEmail,secondaryBinding,device),device);
    }

    private SessionGrant registerOwner(UUID owner,String ownerEmail,String ownerBinding,
            WebAuthnTestAuthenticator device)throws Exception {
        UUID transaction=service.start(ownerEmail,PASSWORD,HOST,ownerBinding);
        var registration=service.options(transaction,ownerBinding,HOST,true);
        service.webauthn(transaction,ownerBinding,HOST,registration.id(),
            device.registration(registration.publicKey(),HOST,"https://"+HOST,true),true);
        var authentication=service.options(transaction,ownerBinding,HOST,false);
        service.webauthn(transaction,ownerBinding,HOST,authentication.id(),
            device.assertion(authentication.publicKey(),HOST,"https://"+HOST,true,device.nextCounter()),false);
        return service.finish(transaction,ownerBinding,HOST);
    }

    private UUID proveStepUp(SessionGrant grant,OperationIntent intent,WebAuthnTestAuthenticator device)throws Exception {
        var challenge=stepUp.begin(grant,intent);
        stepUp.verify(grant,challenge.id(),
            device.assertion(challenge.publicKey(),HOST,"https://"+HOST,true,device.nextCounter()));
        return challenge.id();
    }
    private void executeAsActor(UUID actor,String sql,Object...parameters) {
        try(var connection=java.sql.DriverManager.getConnection(url,user,migrationPassword)) {
            connection.setAutoCommit(false);
            try(var actorStatement=connection.prepareStatement(
                    "SELECT set_config('hich.security.actor_id',?,true)")) {
                actorStatement.setString(1,actor.toString());
                actorStatement.execute();
            }
            try(var statement=connection.prepareStatement(sql)) {
                for(int index=0;index<parameters.length;index++) statement.setObject(index+1,parameters[index]);
                statement.executeUpdate();
            }
            connection.commit();
        } catch(java.sql.SQLException failure) {
            throw new IllegalStateException("Unable to execute attributed authorization fixture",failure);
        }
    }
    @Test void challengeConsumptionIsAtomicSingleUseAndInvalidResponsesStayBurned() throws Exception{
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();var options=service.options(transaction,binding,HOST,true);
        String wrong=device.registration(options.publicKey(),HOST,"https://"+OTHER,true);
        assertThatThrownBy(()->service.webauthn(transaction,binding,HOST,options.id(),wrong,true)).isExactlyInstanceOf(ProtectionFailure.class);
        String correct=device.registration(options.publicKey(),HOST,"https://"+HOST,true);
        assertThatThrownBy(()->service.webauthn(transaction,binding,HOST,options.id(),correct,true)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(runtime.queryForObject("SELECT consumed_at IS NOT NULL FROM platform.mfa_challenges WHERE id=?",Boolean.class,options.id())).isTrue();
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.webauthn_credentials WHERE account_id=?",Long.class,account)).isZero();
    }
    @Test void expiredChallengeAndWrongBrowserOrCompanyCannotCompleteCeremonies() throws Exception{
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();var options=service.options(transaction,binding,HOST,true);
        fixtures.update("UPDATE platform.mfa_challenges SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",options.id());
        String reply=device.registration(options.publicKey(),HOST,"https://"+HOST,true);
        assertThatThrownBy(()->service.webauthn(transaction,binding,HOST,options.id(),reply,true)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.options(transaction,"wrong-binding",HOST,true)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.options(transaction,binding,OTHER,true)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void requiredMfaAndRoleChangesCannotUsePasswordGrantsOrOldAuthority() throws Exception{
        var accountRecord=identities.findAccount(email).orElseThrow();assertThat(identities.createGrant(account,accountRecord.securityVersion(),HOST)).isEmpty();
        UUID transaction=start();var device=new WebAuthnTestAuthenticator();enroll(transaction,device);SessionGrant grant=service.finish(transaction,binding,HOST);
        fixtures.update("UPDATE platform.company_memberships SET auth_role='PLATFORM_ADMIN' WHERE account_id=? AND company_id=?",account,company);
        assertThat(identities.isCurrent(grant)).isFalse();assertThatThrownBy(this::start).isExactlyInstanceOf(ProtectionFailure.class);
        service.requestToken(email,HOST,"PASSWORD_RESET");String reset=delivery("PASSWORD_RESET");
        assertThatThrownBy(()->service.resetPassword(reset,HOST,NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void totpConfirmationAndAuthenticationShareAtomicReplayProtection() throws Exception{
        UUID transaction=start();String uri=service.enrollTotp(transaction,binding,HOST);String secret=secret(uri);
        String code=totp(secret,Instant.now());service.totp(transaction,binding,HOST,code,true);
        UUID next=start();assertThatThrownBy(()->service.totp(next,binding,HOST,code,false)).isExactlyInstanceOf(ProtectionFailure.class);
        String later=totp(secret,Instant.now().plusSeconds(30));service.totp(next,binding,HOST,later,false);
        var grant=service.finish(next,binding,HOST);assertThat(grant.assurance()).isEqualTo("TOTP");assertThat(identities.isCurrent(grant)).isTrue();
        fixtures.update("UPDATE platform.auth_companies SET totp_allowed=false WHERE id=?",company);assertThat(identities.isCurrent(grant)).isFalse();
    }
    @Test void recoveryCodeHasAtMostOneConcurrentConsumerAndNeverGrantsErpAccess() throws Exception{
        UUID enrolled=start();enroll(enrolled,new WebAuthnTestAuthenticator());String code=service.recoveryCodes(enrolled,binding,HOST).getFirst();
        UUID first=start(),second=start();var ready=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->recoverAfter(ready,first,code));var b=workers.submit(()->recoverAfter(ready,second,code));ready.countDown();
            assertThat((a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        }
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.mfa_transactions WHERE account_id=? AND recovery_only",Long.class,account)).isEqualTo(1);
        for(UUID id:List.of(first,second))assertThatThrownBy(()->service.finish(id,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    private boolean recoverAfter(CountDownLatch ready,UUID id,String code)throws InterruptedException{
        ready.await();try{service.recover(id,binding,HOST,code);return true;}catch(ProtectionFailure rejected){return false;}
    }
    @Test void enrollmentThenReplacementKeepsLastUsableFactorProtected() throws Exception{
        UUID transaction=start();var first=new WebAuthnTestAuthenticator();enroll(transaction,first);
        assertThatThrownBy(()->service.remove(transaction,binding,HOST,first.id.toBase64UrlString())).isExactlyInstanceOf(ProtectionFailure.class);
        var second=new WebAuthnTestAuthenticator();enroll(transaction,second);service.remove(transaction,binding,HOST,first.id.toBase64UrlString());
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.webauthn_credentials WHERE account_id=? AND company_id=?",Long.class,account,company)).isEqualTo(1);
        assertThatThrownBy(()->service.remove(transaction,binding,HOST,second.id.toBase64UrlString())).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void passwordChangeAndRevokeAllFencePreviouslyIssuedSessions() throws Exception{
        var device=new WebAuthnTestAuthenticator();UUID transaction=start();enroll(transaction,device);SessionGrant grant=service.finish(transaction,binding,HOST);
        UUID change=start();authenticate(change,device);service.changePassword(change,binding,HOST,PASSWORD,NEW_PASSWORD);
        assertThat(identities.isCurrent(grant)).isFalse();assertThat(passwords.matches(NEW_PASSWORD,identities.findAccount(email).orElseThrow().passwordHash())).isTrue();
        assertThatThrownBy(()->service.finish(change,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        UUID next=service.start(email,NEW_PASSWORD,HOST,binding);authenticate(next,device);SessionGrant newGrant=service.finish(next,binding,HOST);
        UUID revoke=service.start(email,NEW_PASSWORD,HOST,binding);authenticate(revoke,device);service.revokeAll(revoke,binding,HOST);assertThat(identities.isCurrent(newGrant)).isFalse();
    }
    @Test void incorrectCurrentPasswordDoesNotChangeCredentials() throws Exception{
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());String before=identities.findAccount(email).orElseThrow().passwordHash();
        assertThatThrownBy(()->service.changePassword(transaction,binding,HOST,"incorrect",NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(identities.findAccount(email).orElseThrow().passwordHash()).isEqualTo(before);
    }
    @Test void emailVerificationIsPurposeHostVersionBoundAndSingleUse(){
        fixtures.update("UPDATE platform.accounts SET status='PENDING',email_verified=false WHERE id=?",account);
        service.requestToken(email,HOST,"EMAIL_VERIFY");String token=delivery("EMAIL_VERIFY");
        assertThat(runtime.queryForObject("SELECT platform.consume_account_token(?,?,?,?)",Boolean.class,crypto.digest(token),HOST,"EMAIL_VERIFY",null)).isFalse();
        assertThatThrownBy(()->service.verifyEmail(token,OTHER)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.resetPassword(token,HOST,NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
        service.verifyEmail(token,HOST);assertThat(identities.findAccount(email).orElseThrow().emailVerified()).isTrue();
        assertThatThrownBy(()->service.verifyEmail(token,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void expiredResetTokenDoesNotChangePasswordAndUnknownEmailDoesNotQueueDelivery(){
        service.requestToken(email,HOST,"PASSWORD_RESET");String token=delivery("PASSWORD_RESET");
        fixtures.update("UPDATE platform.account_tokens SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE account_id=?",account);
        assertThatThrownBy(()->service.resetPassword(token,HOST,NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(passwords.matches(PASSWORD,identities.findAccount(email).orElseThrow().passwordHash())).isTrue();
        service.requestToken("unknown-"+email,HOST,"PASSWORD_RESET");
        assertThat(fixtures.queryForObject("SELECT count(*) FROM platform.security_notifications WHERE account_id=? AND kind='PASSWORD_RESET'",Long.class,account)).isEqualTo(1);
    }
    @Test void resetRevokesSessionsWithoutRemovingRequiredMfaOrFactors() throws Exception{
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());SessionGrant grant=service.finish(transaction,binding,HOST);
        service.requestToken(email,HOST,"PASSWORD_RESET");String token=delivery("PASSWORD_RESET");service.resetPassword(token,HOST,NEW_PASSWORD);
        assertThat(identities.isCurrent(grant)).isFalse();assertThat(identities.findAccount(email).orElseThrow().mfaRequired()).isTrue();
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.webauthn_credentials WHERE account_id=?",Long.class,account)).isEqualTo(1);
        assertThatThrownBy(()->service.resetPassword(token,HOST,NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void partialHttpFlowRotatesSessionAndCannotBypassMfaOrCsrf() throws Exception{
        var csrf=mvc.perform(request(get("/api/v1/auth/csrf"))).andExpect(status().isOk()).andReturn();Cookie original=cookie(csrf);
        String token=json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        scopes.add("start:"+email);
        var begun=mvc.perform(request(post("/api/v1/auth/mfa/start")).header("Origin","https://"+HOST).contentType("application/x-www-form-urlencoded")
            .cookie(original).header("X-CSRF-TOKEN",token).param("email",email).param("password",PASSWORD)).andExpect(status().isAccepted()).andReturn();
        Cookie partial=cookie(begun);assertThat(partial.getValue()).isNotEqualTo(original.getValue());
        Session partialSession=sessions.findById(sessionId(partial));assertThat(partialSession).isNotNull();
        UUID id=(UUID)partialSession.getAttribute(ProtectionSessionState.TRANSACTION);scopes.add("finish:"+id);
        mvc.perform(request(get("/api/v1/auth/session")).cookie(partial)).andExpect(status().isUnauthorized());
        mvc.perform(request(post("/api/v1/auth/mfa/finish")).header("Origin","https://"+HOST).cookie(partial).header("X-CSRF-TOKEN",token))
            .andExpect(status().isUnauthorized()).andExpect(content().json("{\"code\":\"authentication_failed\"}"));
        mvc.perform(request(post("/api/v1/auth/mfa/finish")).header("Origin","https://"+HOST).cookie(partial)).andExpect(status().isForbidden());
        mvc.perform(request(post("/api/v1/auth/logout")).header("Origin","https://"+HOST).cookie(partial).header("X-CSRF-TOKEN",token)).andExpect(status().isNoContent());
        assertThat(runtime.queryForObject("SELECT completed FROM platform.mfa_transactions WHERE id=?",Boolean.class,id)).isTrue();
    }
    @Test void actualHttpWebAuthnFlowSavesRedisContextRotatesCsrfAndLogsOutDurably() throws Exception{
        var bootstrap=mvc.perform(request(get("/api/v1/auth/csrf"))).andExpect(status().isOk()).andReturn();Cookie anonymous=cookie(bootstrap);
        String token=json.readTree(bootstrap.getResponse().getContentAsString()).get("token").asText();scopes.add("start:"+email);
        var start=mvc.perform(request(post("/api/v1/auth/mfa/start")).header("Origin","https://"+HOST).contentType("application/x-www-form-urlencoded")
            .cookie(anonymous).header("X-CSRF-TOKEN",token).param("email",email).param("password",PASSWORD)).andExpect(status().isAccepted()).andReturn();
        Cookie partial=cookie(start);Session partialSession=sessions.findById(sessionId(partial));assertThat(partialSession).isNotNull();
        UUID id=(UUID)partialSession.getAttribute(ProtectionSessionState.TRANSACTION);
        scopes.add("options:"+id);scopes.add("verify:"+id);scopes.add("finish:"+id);
        var creation=mvc.perform(request(post("/api/v1/auth/mfa/webauthn/registration/options")).header("Origin","https://"+HOST)
            .cookie(partial).header("X-CSRF-TOKEN",token)).andExpect(status().isOk()).andReturn();
        var options=json.readTree(creation.getResponse().getContentAsString());var device=new WebAuthnTestAuthenticator();
        String reply=device.registration(options.get("publicKey").toString(),HOST,"https://"+HOST,true);
        var registered=mvc.perform(request(post("/api/v1/auth/mfa/webauthn/registration/complete")).header("Origin","https://"+HOST)
            .cookie(partial).header("X-CSRF-TOKEN",token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("challengeId",options.get("challengeId").asText(),"credential",reply))))
            .andExpect(status().isNoContent()).andReturn();
        Cookie verified=cookie(registered);assertThat(verified.getValue()).isNotEqualTo(partial.getValue());
        mvc.perform(request(get("/api/v1/auth/session")).cookie(verified)).andExpect(status().isUnauthorized());
        mvc.perform(request(post("/api/v1/auth/mfa/finish")).header("Origin","https://"+HOST).cookie(verified).header("X-CSRF-TOKEN",token)).andExpect(status().isUnauthorized());
        var assertionOptions=mvc.perform(request(post("/api/v1/auth/mfa/webauthn/authentication/options")).header("Origin","https://"+HOST)
            .cookie(verified).header("X-CSRF-TOKEN",token)).andExpect(status().isOk()).andReturn();
        var assertion=json.readTree(assertionOptions.getResponse().getContentAsString());
        String proof=device.assertion(assertion.get("publicKey").toString(),HOST,"https://"+HOST,true,device.nextCounter());
        var asserted=mvc.perform(request(post("/api/v1/auth/mfa/webauthn/authentication/complete")).header("Origin","https://"+HOST)
            .cookie(verified).header("X-CSRF-TOKEN",token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("challengeId",assertion.get("challengeId").asText(),"credential",proof))))
            .andExpect(status().isNoContent()).andReturn();
        Cookie assured=cookie(asserted);
        var finished=mvc.perform(request(post("/api/v1/auth/mfa/finish")).header("Origin","https://"+HOST).cookie(assured).header("X-CSRF-TOKEN",token))
            .andExpect(status().isNoContent()).andReturn();Cookie authenticated=cookie(finished);
        assertThat(authenticated.getValue()).isNotEqualTo(assured.getValue());
        mvc.perform(request(get("/api/v1/auth/session")).cookie(authenticated)).andExpect(status().isOk())
            .andExpect(jsonPath("$.assurance").value("WEBAUTHN")).andExpect(jsonPath("$.companyId").value(company.toString()));
        mvc.perform(request(get("/api/v1/auth/session")).cookie(anonymous)).andExpect(status().isUnauthorized());
        mvc.perform(request(post("/api/v1/auth/logout")).header("Origin","https://"+HOST).cookie(authenticated).header("X-CSRF-TOKEN",token)).andExpect(status().isForbidden());
        var fresh=mvc.perform(request(get("/api/v1/auth/csrf")).cookie(authenticated)).andExpect(status().isOk()).andReturn();
        String updated=json.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(request(post("/api/v1/auth/logout")).header("Origin","https://"+HOST).cookie(authenticated).header("X-CSRF-TOKEN",updated)).andExpect(status().isNoContent());
        assertThat(runtime.queryForObject("SELECT revoked_at IS NOT NULL FROM platform.authentication_sessions WHERE mfa_transaction_id=?",Boolean.class,id)).isTrue();
    }
    @Test void notificationStorageFailureRollsBackFactorChangeButNeverRestoresChallenge() throws Exception{
        var failing=new AccountProtectionService(store,verification,passwordPolicy,crypto,ceremonies,
            (a,c,k)->{throw new org.springframework.dao.DataAccessResourceFailureException("injected outbox outage");},java.time.Clock.systemUTC());
        UUID transaction=start();var options=service.options(transaction,binding,HOST,true);var device=new WebAuthnTestAuthenticator();
        String reply=device.registration(options.publicKey(),HOST,"https://"+HOST,true);
        long before=identities.findAccount(email).orElseThrow().securityVersion();
        assertThatThrownBy(()->failing.webauthn(transaction,binding,HOST,options.id(),reply,true))
            .isExactlyInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(runtime.queryForObject("SELECT count(*) FROM platform.webauthn_credentials WHERE account_id=?",Long.class,account)).isZero();
        assertThat(identities.findAccount(email).orElseThrow().securityVersion()).isEqualTo(before);
        assertThat(runtime.queryForObject("SELECT consumed_at IS NOT NULL FROM platform.mfa_challenges WHERE id=?",Boolean.class,options.id())).isTrue();
    }
    @Test void cancellationIsDurableEvenWhenPartialBrowserStateIsReused() throws Exception{
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());service.cancel(transaction,binding,HOST);
        assertThatThrownBy(()->service.finish(transaction,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.options(transaction,binding,HOST,false)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void completedTransactionCancellationAlsoRevokesTheResultingGrant() throws Exception{
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());var grant=service.finish(transaction,binding,HOST);
        assertThat(identities.isCurrent(grant)).isTrue();service.cancel(transaction,binding,HOST);assertThat(identities.isCurrent(grant)).isFalse();
    }
    @Test void companyAdministratorsRetainShorterIdleAndAbsoluteDeadlines() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='ADMIN' WHERE account_id=? AND company_id=?",account,company);
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());var grant=service.finish(transaction,binding,HOST);
        assertThat(grant.privileged()).isTrue();assertThat(grant.idleLimit().toMinutes()).isEqualTo(15);assertThat(grant.absoluteLimit().toHours()).isEqualTo(8);
        assertThat(identities.isCurrent(grant)).isTrue();
    }
    @Test void optionalMfaOptInCannotBeBypassedDuringRestrictedRecovery() throws Exception{
        fixtures.update("UPDATE platform.accounts SET mfa_required=false WHERE id=?",account);
        fixtures.update("UPDATE platform.auth_companies SET mfa_required=false WHERE id=?",company);
        fixtures.update("UPDATE platform.company_memberships SET mfa_required=false WHERE account_id=? AND company_id=?",account,company);
        UUID enrolled=start();enroll(enrolled,new WebAuthnTestAuthenticator());String code=service.recoveryCodes(enrolled,binding,HOST).getFirst();
        UUID recovery=start();service.recover(recovery,binding,HOST,code);
        var current=identities.findAccount(email).orElseThrow();
        assertThat(identities.createGrant(account,current.securityVersion(),HOST)).isEmpty();
        assertThat(runtime.queryForObject("SELECT mfa_required FROM platform.company_memberships WHERE account_id=? AND company_id=?",Boolean.class,account,company)).isTrue();
        assertThat(runtime.queryForObject("SELECT mfa_enrolled FROM platform.company_memberships WHERE account_id=? AND company_id=?",Boolean.class,account,company)).isTrue();
        service.requestToken(email,HOST,"MFA_ENROLL");String enrollmentToken=delivery("MFA_ENROLL");
        assertThatThrownBy(()->service.approveEnrollment(enrollmentToken,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.finish(recovery,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
    }
    @Test void firstFactorRequiresFreshCompanyBoundApprovalThatCannotBeReused() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET mfa_enrollment_until=NULL WHERE account_id=?",account);
        UUID unapproved=start();
        assertThatThrownBy(()->service.options(unapproved,binding,HOST,true)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.enrollTotp(unapproved,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        service.requestToken(email,HOST,"MFA_ENROLL");String token=delivery("MFA_ENROLL");
        assertThatThrownBy(()->service.approveEnrollment(token,OTHER)).isExactlyInstanceOf(ProtectionFailure.class);
        service.approveEnrollment(token,HOST);
        assertThatThrownBy(()->service.approveEnrollment(token,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        UUID approved=start();enroll(approved,new WebAuthnTestAuthenticator());
        assertThat(runtime.queryForObject("SELECT mfa_enrollment_until IS NULL FROM platform.company_memberships WHERE account_id=? AND company_id=?",Boolean.class,account,company)).isTrue();
        assertThat(identities.isCurrent(service.finish(approved,binding,HOST))).isTrue();
    }
    @Test void transactionAttemptBudgetNeverDisablesTheAccount(){
        UUID transaction=start();for(int i=0;i<10;i++)store.attempt(transaction,crypto.digest(binding),HOST);
        assertThatThrownBy(()->store.attempt(transaction,crypto.digest(binding),HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(identities.findAccount(email).orElseThrow().status()).isEqualTo("ACTIVE");assertThat(start()).isNotNull();
    }
    @Test void ordinaryCompanyMfaCannotReplaceThePasswordOfAHardwarePrivilegedIdentity() throws Exception{
        fixtures.update("UPDATE platform.company_memberships SET auth_role='PLATFORM_ADMIN' WHERE account_id=? AND company_id=?",account,otherCompany);
        UUID transaction=start();enroll(transaction,new WebAuthnTestAuthenticator());
        assertThatThrownBy(()->service.changePassword(transaction,binding,HOST,PASSWORD,NEW_PASSWORD)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThat(passwords.matches(PASSWORD,identities.findAccount(email).orElseThrow().passwordHash())).isTrue();
    }
    @Test void runtimeCannotExtendSecurityDeadlinesOrClaimEmailDelivery(){
        for(String sql:List.of(
                "UPDATE platform.account_tokens SET expires_at=CURRENT_TIMESTAMP+INTERVAL '1 year' WHERE account_id=?",
                "UPDATE platform.mfa_challenges SET expires_at=CURRENT_TIMESTAMP+INTERVAL '1 year' WHERE transaction_id=?",
                "UPDATE platform.mfa_transactions SET expires_at=CURRENT_TIMESTAMP+INTERVAL '1 year' WHERE account_id=?",
                "UPDATE platform.security_notifications SET delivered_at=CURRENT_TIMESTAMP WHERE account_id=?")){
            try{runtime.update(sql,account);fail("Restricted runtime write unexpectedly succeeded");}
            catch(org.springframework.dao.DataAccessException denied){
                Throwable cause=denied.getMostSpecificCause();assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                assertThat(((java.sql.SQLException)cause).getSQLState()).isEqualTo("42501");
            }
        }
    }
    private UUID start(){return service.start(email,PASSWORD,HOST,binding);}
    private void enroll(UUID transaction,WebAuthnTestAuthenticator device)throws Exception{
        var options=service.options(transaction,binding,HOST,true);service.webauthn(transaction,binding,HOST,options.id(),device.registration(options.publicKey(),HOST,"https://"+HOST,true),true);
        assertThatThrownBy(()->service.finish(transaction,binding,HOST)).isExactlyInstanceOf(ProtectionFailure.class);
        authenticate(transaction,device);
    }
    private void authenticate(UUID transaction,WebAuthnTestAuthenticator device)throws Exception{
        var options=service.options(transaction,binding,HOST,false);service.webauthn(transaction,binding,HOST,options.id(),device.assertion(options.publicKey(),HOST,"https://"+HOST,true,device.nextCounter()),false);
    }
    private String delivery(String purpose){String encrypted=fixtures.queryForObject("SELECT encrypted_delivery FROM platform.security_notifications WHERE account_id=? AND kind=? AND encrypted_delivery IS NOT NULL ORDER BY created_at DESC LIMIT 1",String.class,account,purpose);return crypto.open("delivery:"+HOST+":"+purpose,encrypted);}
    private static String secret(String uri){return uri.split("secret=",2)[1].split("&",2)[0];}
    private static String totp(String secret,Instant at)throws Exception{return new TimeBasedOneTimePasswordGenerator().generateOneTimePasswordString(new SecretKeySpec(Base32.decode(secret),"HmacSHA1"),at,java.util.Locale.ROOT);}
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder){return builder.secure(true).header("Host",HOST).with(req->{req.setServerName(HOST);req.setServletPath(req.getRequestURI());req.setRemoteAddr(source);return req;});}
    private Cookie cookie(MvcResult result){String header=result.getResponse().getHeaders("Set-Cookie").stream().filter(s->s.startsWith("__Host-HICHSESSION=")).findFirst().orElseThrow();String value=header.split(";",2)[0].substring("__Host-HICHSESSION=".length());ownedSessions.add(sessionId(new Cookie("__Host-HICHSESSION",value)));return new Cookie("__Host-HICHSESSION",value);}
    private static String sessionId(Cookie cookie){return new String(Base64.getDecoder().decode(cookie.getValue()),StandardCharsets.UTF_8);}
    private static String testKey(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getEncoder().encodeToString(value);}
    private static String blocklist(){try{var path=Files.createTempFile("hich-mfa-test-blocklist-",".txt");Files.writeString(path,"0".repeat(64));path.toFile().deleteOnExit();return path.toString();}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
