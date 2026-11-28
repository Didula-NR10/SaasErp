package com.saaserp.erp.platform.identity.api;

import com.saaserp.erp.platform.identity.application.RegistrationService;
import com.saaserp.erp.platform.identity.domain.SessionGrant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final RegistrationService registration;
    public AuthenticationController(RegistrationService registration) { this.registration = registration; }
    @GetMapping("/csrf") public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @ModelAttribute RegistrationRequest request) {
        registration.register(request.email(), request.password());
    }
    @GetMapping("/session") public SessionResponse session(@AuthenticationPrincipal SessionGrant grant) {
        return new SessionResponse(grant.accountId(), grant.companyId(), grant.hostname(), grant.assurance());
    }
    public record RegistrationRequest(@NotBlank @Size(max=254) String email, @NotBlank @Size(min=15,max=128) String password) {
        @Override public String toString() { return "RegistrationRequest[redacted]"; }
    }
    public record CsrfResponse(String headerName, String token) { }
    public record SessionResponse(UUID accountId, UUID companyId, String hostname, String assurance) { }
}
