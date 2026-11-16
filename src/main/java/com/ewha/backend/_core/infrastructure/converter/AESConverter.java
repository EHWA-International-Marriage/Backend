package com.ewha.backend._core.infrastructure.converter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.ewha.backend._core.config.properties.SecurityProperties;

/**
 * 연락처 등 개인정보 컬럼 암호화(AES-256-GCM). 사용: {@code @Convert(converter = AESConverter.class)}
 * <p>
 * 저장 형식은 Base64(IV 12바이트 + 암호문 + 인증 태그). 같은 값도 매번 다른 암호문이 되므로 이 컬럼으로 WHERE 검색은 할 수 없다.
 * 검색이 필요하면 별도 해시 컬럼을 둔다. 예외 메시지에 평문·암호문을 넣지 않는다.
 */
@Converter
public class AESConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    KEY_BYTES      = 32;
    private static final int    IV_BYTES       = 12;
    private static final int    TAG_BITS       = 128;

    private final SecretKey    key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AESConverter(SecurityProperties securityProperties) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(securityProperties.encryption().aesKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AES_SECRET_KEY는 Base64 형식이어야 합니다.");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("AES_SECRET_KEY는 256bit(32바이트)여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64
                    .getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인정보 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] data = Base64.getDecoder().decode(dbData);
            if (data.length <= IV_BYTES) {
                throw new IllegalStateException("개인정보 복호화에 실패했습니다.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] decrypted = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException |
                 IllegalArgumentException e) {
            throw new IllegalStateException("개인정보 복호화에 실패했습니다.", e);
        }
    }
}
