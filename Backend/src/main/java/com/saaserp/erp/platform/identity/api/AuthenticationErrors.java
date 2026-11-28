package com.saaserp.erp.platform.identity.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {AuthenticationController.class, AccountProtectionController.class})
public class AuthenticationErrors {
    @ExceptionHandler({com.saaserp.erp.platform.identity.application.ProtectionFailure.class,
            org.springframework.security.core.AuthenticationException.class})
    public ResponseEntity<Map<String,String>> denied(){return ResponseEntity.status(401).body(Map.of("code","authentication_failed"));}
    @ExceptionHandler(com.saaserp.erp.platform.identity.application.ProtectionRateLimit.Exceeded.class)
    public ResponseEntity<Map<String,String>> limited(){return ResponseEntity.status(429).body(Map.of("code","too_many_requests"));}

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", "invalid_request"));
    }
    @ExceptionHandler({IllegalStateException.class, org.springframework.dao.DataAccessException.class, org.springframework.security.authentication.AuthenticationServiceException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "security_unavailable"));
    }
}
