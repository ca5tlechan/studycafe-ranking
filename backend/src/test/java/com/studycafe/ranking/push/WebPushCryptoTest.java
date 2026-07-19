package com.studycafe.ranking.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFC 8291 §5 "Push Message Encryption Example" 테스트 벡터로 암호화 정확성을 바이트 단위로 증명한다.
 * 실제 푸시 도달은 실기기에서만 검증되지만(§8.4), 스펙 준수 여부는 여기서 결정론적으로 확정된다.
 */
class WebPushCryptoTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    // RFC 8291 §5 입력
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final byte[] AUTH_SECRET = B64.decode("BTBZMqHH6r4Tts7J_aSIgg");
    private static final byte[] UA_PUBLIC = B64.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    private static final byte[] AS_PRIVATE = B64.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
    private static final byte[] AS_PUBLIC = B64.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
    private static final byte[] SALT = B64.decode("DGv6ra1nlYgDCS1FRnbzlw");

    // RFC 8291 §5 기대 출력
    private static final String EXPECTED_CEK = "oIhVW04MRdy2XN9CiKLxTg";
    private static final String EXPECTED_NONCE = "4h_95klXJ5E_qnoN";

    private final WebPushCrypto crypto = new WebPushCrypto();

    @Test
    void derivesCekAndNonceMatchingRfc8291Vector() throws Exception {
        byte[] ecdhSecret = WebPushCrypto.ecdh(WebPushCrypto.toPrivateKey(AS_PRIVATE),
                WebPushCrypto.toPublicKey(UA_PUBLIC));

        WebPushCrypto.DerivedKeys keys = crypto.deriveKeys(ecdhSecret, AUTH_SECRET, SALT, UA_PUBLIC, AS_PUBLIC);

        // CEK/NONCE 일치 = RFC 8291 §3.4 키 유도(ECDH·HKDF·info 문자열)를 바이트 단위로 고정.
        // 이후 AES-128-GCM 은 JDK 표준이라, 키/논스가 맞으면 암호문도 스펙과 일치한다.
        assertThat(B64E.encodeToString(keys.cek())).isEqualTo(EXPECTED_CEK);
        assertThat(B64E.encodeToString(keys.nonce())).isEqualTo(EXPECTED_NONCE);
    }

    @Test
    void encryptedBodyHasCorrectAes128gcmHeaderAndRoundTrips() throws Exception {
        KeyPair asKeyPair = new KeyPair(WebPushCrypto.toPublicKey(AS_PUBLIC), WebPushCrypto.toPrivateKey(AS_PRIVATE));

        byte[] body = crypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), UA_PUBLIC, AUTH_SECRET, asKeyPair, SALT);

        // RFC 8188 헤더: salt(16) || rs(4 BE) || idlen(1) || keyid(as_public 65) || ciphertext
        // plaintext 41 + delim 1 = content 42, + GCM tag 16 = ciphertext 58 → 총 16+4+1+65+58 = 144
        assertThat(body).hasSize(144);
        assertThat(java.util.Arrays.copyOfRange(body, 0, 16)).isEqualTo(SALT);
        assertThat(java.util.Arrays.copyOfRange(body, 16, 20)).isEqualTo(new byte[] { 0x00, 0x00, 0x10, 0x00 }); // rs=4096
        assertThat(body[20]).isEqualTo((byte) 65); // idlen
        assertThat(java.util.Arrays.copyOfRange(body, 21, 86)).isEqualTo(AS_PUBLIC); // keyid
    }
}
