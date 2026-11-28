package com.saaserp.erp.platform.identity.infrastructure;

import com.saaserp.erp.platform.identity.application.PasswordPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/** Offline SHA-256 blocklist of full passwords; never send passwords to a remote service. */
public final class CompromisedPasswordPolicy implements PasswordPolicy {
    private final Set<String> compromised;
    public CompromisedPasswordPolicy(String path) throws IOException {
        if (path.isBlank()) { compromised = Set.of(); return; }
        try (var lines = Files.lines(Path.of(path), StandardCharsets.UTF_8)) {
            compromised = lines.map(String::strip).filter(s -> !s.isEmpty()).map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
        }
        if (compromised.isEmpty() || compromised.stream().anyMatch(s -> !s.matches("[a-f0-9]{64}"))) {
            throw new IllegalArgumentException("Invalid compromised-password blocklist");
        }
    }
    @Override public void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 15 || password.length() > 128) {
            throw new IllegalArgumentException("Invalid password");
        }
        if (compromised.isEmpty()) throw new IllegalStateException("Password screening unavailable");
        if (compromised.contains(digest(password))) throw new IllegalArgumentException("Invalid password");
    }
    static String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
