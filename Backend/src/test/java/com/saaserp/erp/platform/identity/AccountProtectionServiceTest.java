package com.saaserp.erp.platform.identity;

import com.saaserp.erp.platform.identity.application.*;
import com.saaserp.erp.platform.identity.domain.MfaPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountProtectionServiceTest {
    private final ProtectionRepository store=mock(ProtectionRepository.class);
    private final PasswordVerification passwords=mock(PasswordVerification.class);
    private final FactorCryptography crypto=mock(FactorCryptography.class);
    private final WebAuthnCeremonies webauthn=mock(WebAuthnCeremonies.class);
    private final SecurityNotifications events=mock(SecurityNotifications.class);
    private final UUID transaction=UUID.randomUUID(),account=UUID.randomUUID(),company=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    private final AccountProtectionService service=new AccountProtectionService(store,passwords,p->{},crypto,webauthn,events,Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void defaults(){
        when(store.atomic(any())).thenAnswer(i->i.getArgument(0,Supplier.class).get());
        when(crypto.digest(anyString())).thenAnswer(i->i.getArgument(0));
    }
    private ProtectionRepository.State state(boolean enrolled,boolean recovery,String method,boolean hardware,boolean totp){
        return new ProtectionRepository.State(transaction,account,company,"a.test",1,1,1,
            new MfaPolicy(MfaPolicy.Role.EMPLOYEE,true,true,true,hardware,totp),!enrolled,recovery,method,method==null?null:now);
    }
    @Test void passwordOnlyAndRestrictedRecoveryTransactionsCannotMintCompanySessions(){
        for(var s:new ProtectionRepository.State[]{state(false,false,null,false,true),state(true,true,"WEBAUTHN",false,true)}){
            when(store.state(transaction,"binding","a.test",true)).thenReturn(s);
            assertThatThrownBy(()->service.finish(transaction,"binding","a.test")).isExactlyInstanceOf(ProtectionFailure.class);
        }
        verify(store,never()).finish(any());
    }
    @Test void newFactorsCannotBeAddedToAnEnrolledAccountWithoutRecentMfa(){
        when(store.state(transaction,"binding","a.test",true)).thenReturn(state(true,false,null,false,true));
        assertThatThrownBy(()->service.options(transaction,"binding","a.test",true)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.enrollTotp(transaction,"binding","a.test")).isExactlyInstanceOf(ProtectionFailure.class);
        verifyNoInteractions(webauthn);
    }
    @Test void hardwarePolicyCannotBeDowngradedThroughTotpOrRecovery(){
        when(store.state(transaction,"binding","a.test",true)).thenReturn(state(true,false,null,true,true));
        assertThatThrownBy(()->service.totp(transaction,"binding","a.test","123456",false)).isExactlyInstanceOf(ProtectionFailure.class);
        assertThatThrownBy(()->service.recover(transaction,"binding","a.test","backup")).isExactlyInstanceOf(ProtectionFailure.class);
        verify(store,never()).acceptTotp(any(),anyLong(),anyBoolean());verify(store,never()).recover(any(),anyString());
    }
    @Test void totpReplayFailsBeforeVerificationOrSessionAuthority(){
        var s=state(true,false,null,false,true);when(store.state(transaction,"binding","a.test",true)).thenReturn(s);
        when(store.totp(s,false)).thenReturn("sealed");when(crypto.open(anyString(),eq("sealed"))).thenReturn("secret");
        when(crypto.matchingTotpStep("secret","123456",now)).thenReturn(10L);when(store.acceptTotp(s,10,false)).thenReturn(false);
        assertThatThrownBy(()->service.totp(transaction,"binding","a.test","123456",false)).isExactlyInstanceOf(ProtectionFailure.class);
        verify(store,never()).verified(any(),anyString());
    }
    @Test void challengeIsBurnedBeforeNativeVerificationAndNotRestoredOnFailure(){
        var s=state(true,false,null,false,false);when(store.state(transaction,"binding","a.test",true)).thenReturn(s);
        UUID challenge=UUID.randomUUID();when(store.takeChallenge(transaction,challenge,"AUTHENTICATE","binding","a.test")).thenReturn("options");
        doThrow(new ProtectionFailure()).when(webauthn).authenticate(s,"options","bad");
        assertThatThrownBy(()->service.webauthn(transaction,"binding","a.test",challenge,"bad",false)).isExactlyInstanceOf(ProtectionFailure.class);
        var ordered=inOrder(store,webauthn);ordered.verify(store).attempt(transaction,"binding","a.test");
        ordered.verify(store).takeChallenge(transaction,challenge,"AUTHENTICATE","binding","a.test");ordered.verify(webauthn).authenticate(s,"options","bad");
        verify(store,never()).verified(any(),anyString());
    }
    @Test void removalOfLastUsableFactorAndStaleMfaAreDenied(){
        var s=state(true,false,"WEBAUTHN",false,false);when(store.state(transaction,"binding","a.test",true)).thenReturn(s);when(store.factorCount(s)).thenReturn(1);
        assertThatThrownBy(()->service.remove(transaction,"binding","a.test","TOTP")).isExactlyInstanceOf(ProtectionFailure.class);
        var stale=new ProtectionRepository.State(transaction,account,company,"a.test",1,1,1,s.policy(),false,false,"WEBAUTHN",now.minusSeconds(300));
        when(store.state(transaction,"binding","a.test",true)).thenReturn(stale);
        assertThatThrownBy(()->service.finish(transaction,"binding","a.test")).isExactlyInstanceOf(ProtectionFailure.class);
        verify(store,never()).deleteCredential(any(),any());
    }
    @Test void unavailableWriterAuthorityNeverCallsFactorVerifier(){
        when(store.state(transaction,"binding","a.test",true)).thenThrow(new DataAccessResourceFailureException("test outage"));
        assertThatThrownBy(()->service.options(transaction,"binding","a.test",true)).isExactlyInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(webauthn);
    }
}
