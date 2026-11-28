package com.saaserp.erp.platform.authorization.domain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.HexFormat;

public record OperationIntent(Permission permission, Target target, String targetId, Map<String, String> details) {
    public enum Target { ACCOUNT, COMPANY, MEMBERSHIP, SESSION, CREDENTIAL, RESOURCE }

    public OperationIntent {
        Objects.requireNonNull(permission);
        Objects.requireNonNull(target);
        Objects.requireNonNull(details);
        if (targetId == null || !targetId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid operation target");
        }
        if (details.isEmpty() || details.size() > 64) throw new IllegalArgumentException("Invalid operation details");
        var sorted = new TreeMap<String, String>();
        details.forEach((key, value) -> {
            if (key == null || !key.matches("[a-z][a-zA-Z0-9._-]{0,63}")
                    || value == null || value.length() > 4096) {
                throw new IllegalArgumentException("Invalid operation details");
            }
            sorted.put(key, value);
        });
        details = Map.copyOf(sorted);
    }

    public String digest() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var data = new DataOutputStream(bytes)) {
                write(data, permission.value());
                write(data, target.name());
                write(data, targetId);
                var sorted = new TreeMap<>(details);
                data.writeInt(sorted.size());
                for (var entry : sorted.entrySet()) {
                    write(data, entry.getKey());
                    write(data, entry.getValue());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Unable to digest operation intent", impossible);
        }
    }

    private static void write(DataOutputStream data, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(encoded.length);
        data.write(encoded);
    }
}
