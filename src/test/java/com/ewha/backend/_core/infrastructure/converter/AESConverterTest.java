package com.ewha.backend._core.infrastructure.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ewha.backend.support.SecurityPropertiesFixture;

class AESConverterTest {

    private static final String PHONE = "010-1234-5678";

    private final AESConverter converter = new AESConverter(SecurityPropertiesFixture.create());

    @Test
    @DisplayName("암호화한 값을 복호화하면 원래 값이 된다 (한글 포함)")
    void roundTrip() {
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(PHONE))).isEqualTo(PHONE);
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("홍길동"))).isEqualTo("홍길동");
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(""))).isEmpty();
    }

    @Test
    @DisplayName("같은 값도 매번 다른 암호문이 되고, 암호문에 평문이 드러나지 않는다")
    void randomIv() {
        String first = converter.convertToDatabaseColumn(PHONE);
        String second = converter.convertToDatabaseColumn(PHONE);

        assertThat(first).isNotEqualTo(second).doesNotContain(PHONE);
    }

    @Test
    @DisplayName("null은 null로 저장·조회한다")
    void nullValue() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화에 실패하고, 예외 메시지에 데이터가 들어가지 않는다")
    void tampered() {
        byte[] bytes = Base64.getDecoder().decode(converter.convertToDatabaseColumn(PHONE));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("개인정보 복호화에 실패했습니다.");
    }

    @Test
    @DisplayName("다른 키로 암호화한 값은 복호화할 수 없다")
    void otherKey() {
        String encrypted = new AESConverter(SecurityPropertiesFixture.create()).convertToDatabaseColumn(PHONE);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
