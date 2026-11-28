package com.saaserp.erp.platform.identity.infrastructure;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.saaserp.erp.platform.identity.application.FactorCryptography;
import com.saaserp.erp.platform.identity.application.ProtectionFailure;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;

/** Standard JCA AES-GCM and maintained RFC 6238 implementation; no bespoke cryptographic primitive. */
public final class ProtectedFactorCryptography implements FactorCryptography {
    private final SecureRandom random=new SecureRandom();
    private final Map<String,byte[]> keys=new HashMap<>();
    private final String active;
    private final TimeBasedOneTimePasswordGenerator totp=new TimeBasedOneTimePasswordGenerator();
    public ProtectedFactorCryptography(String active,String configured) {
        this.active=active;
        if(configured.isBlank() && active.isBlank())return; // Disabled encryption is fail-closed per operation.
        try {
            for(String entry:configured.split(",")) {
                String[] parts=entry.split(":",2);
                if(parts.length!=2 || !parts[0].matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException();
                byte[] key=Base64.getDecoder().decode(parts[1]);
                if(key.length!=32 || keys.putIfAbsent(parts[0],key)!=null)throw new IllegalArgumentException();
            }
            if(!keys.containsKey(active))throw new IllegalArgumentException();
        } catch(IllegalArgumentException invalid) {throw new IllegalStateException("Invalid account-protection key configuration");}
    }
    @Override public String randomToken(){byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    @Override public String digest(String value) {
        if(value==null || value.length()>4096)throw new ProtectionFailure();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException impossible){throw new IllegalStateException("Digest unavailable");}
    }
    @Override public String seal(String context,String plaintext) {
        byte[] key=keys.get(active);if(key==null)throw new IllegalStateException("Account protection encryption unavailable");
        byte[] iv=new byte[12];random.nextBytes(iv);
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,key,iv,context,plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] envelope=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,envelope,0,iv.length);System.arraycopy(encrypted,0,envelope,iv.length,encrypted.length);
        return active+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
    }
    @Override public String open(String context,String ciphertext) {
        if(ciphertext==null || ciphertext.length()>8192)throw new ProtectionFailure();
        try {
            String[] parts=ciphertext.split("\\.",2);byte[] key=keys.get(parts[0]);
            if(key==null)throw new IllegalStateException("Required account-protection key unavailable");
            byte[] value=Base64.getUrlDecoder().decode(parts[1]);if(value.length<29)throw new ProtectionFailure();
            return new String(crypt(Cipher.DECRYPT_MODE,key,Arrays.copyOfRange(value,0,12),context,Arrays.copyOfRange(value,12,value.length)),StandardCharsets.UTF_8);
        } catch(IllegalArgumentException|ArrayIndexOutOfBoundsException invalid){throw new ProtectionFailure();}
    }
    private static byte[] crypt(int mode,byte[] key,byte[] iv,String context,byte[] value) {
        try {
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));return cipher.doFinal(value);
        } catch(javax.crypto.AEADBadTagException invalid){throw new ProtectionFailure();}
        catch(GeneralSecurityException unavailable){throw new IllegalStateException("Account protection encryption unavailable");}
    }
    @Override public String newTotpSecret(){byte[] value=new byte[20];random.nextBytes(value);return Base32.toBase32String(value);}
    @Override public String provisioningUri(String secret,String label) {
        return "otpauth://totp/"+URLEncoder.encode(label,StandardCharsets.UTF_8)+"?secret="+secret+"&issuer=Hich&algorithm=SHA1&digits=6&period=30";
    }
    @Override public long matchingTotpStep(String secret,String code,Instant now) {
        if(code==null || !code.matches("[0-9]{6}"))throw new ProtectionFailure();
        var key=new SecretKeySpec(Base32.decode(secret),"HmacSHA1");long current=now.getEpochSecond()/30;
        try {
            for(long step=current+1;step>=current-1;step--) {
                String expected=totp.generateOneTimePasswordString(key,Instant.ofEpochSecond(step*30),java.util.Locale.ROOT);
                if(MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),code.getBytes(StandardCharsets.US_ASCII)))return step;
            }
        } catch(java.security.InvalidKeyException invalid){throw new IllegalStateException("TOTP key unavailable");}
        throw new ProtectionFailure();
    }
}
