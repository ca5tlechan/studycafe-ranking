package com.studycafe.ranking.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * Web Push 메시지 암호화 — RFC 8291(aes128gcm, RFC 8188) 순수 구현.
 * <p>검증된 라이브러리(nl.martijndwars:web-push) 대신 직접 구현한 이유: 그 라이브러리는
 * async-http-client/netty 를 전이 의존으로 끌고 와 Spring Boot 4 의 netty BOM 과 버전이
 * 충돌할 수 있고, 그 충돌은 <b>실제 전송 시점</b>(기기/배포)에만 드러나 로컬에서 검증되지 않는다.
 * 반면 이 구현은 JDK crypto 만 쓰므로 의존성 충돌이 없고, RFC 8291 §5 테스트 벡터로
 * 암호화 정확성을 로컬에서 바이트 단위로 증명한다({@code WebPushCryptoTest}).
 * <p>도달 자체는 HTTPS + 설치형 PWA + 실기기에서만 검증되는 "베스트 에포트"다(CLAUDE.md §8.4).
 */
public final class WebPushCrypto {

    /** RFC 8188 record size. 단일 레코드라 payload 보다 크기만 하면 된다. */
    private static final int RECORD_SIZE = 4096;

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private static final ECParameterSpec P256 = p256Params();

    private final SecureRandom random = new SecureRandom();

    /** 파생된 대칭키(테스트로 RFC 벡터와 대조). */
    record DerivedKeys(byte[] cek, byte[] nonce) {
    }

    /**
     * 프로덕션 경로: 임시(ephemeral) 서버 키쌍과 랜덤 salt 를 생성해 암호화한다.
     *
     * @param plaintext   보낼 원문
     * @param uaPublicRaw 구독 p256dh(65바이트 uncompressed EC point)
     * @param authSecret  구독 auth(16바이트)
     * @return RFC 8188 aes128gcm 바디(그대로 HTTP body 로 전송)
     */
    public byte[] encrypt(byte[] plaintext, byte[] uaPublicRaw, byte[] authSecret) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), random);
            return encrypt(plaintext, uaPublicRaw, authSecret, kpg.generateKeyPair(), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push 암호화 실패", e);
        }
    }

    /**
     * 결정론 경로(테스트/RFC 벡터용): 서버 임시 키쌍과 salt 를 주입한다.
     * as 는 application server(발신자), ua 는 user agent(수신 구독).
     */
    byte[] encrypt(byte[] plaintext, byte[] uaPublicRaw, byte[] authSecret,
                   KeyPair asKeyPair, byte[] salt) {
        try {
            ECPublicKey uaPublic = toPublicKey(uaPublicRaw);
            byte[] asPublicRaw = toRawPublic((ECPublicKey) asKeyPair.getPublic());

            byte[] ecdhSecret = ecdh((ECPrivateKey) asKeyPair.getPrivate(), uaPublic);
            DerivedKeys keys = deriveKeys(ecdhSecret, authSecret, salt, uaPublicRaw, asPublicRaw);

            // aes128gcm 레코드: 원문 뒤에 마지막-레코드 구분자 0x02(추가 패딩 없음).
            byte[] content = Arrays.copyOf(plaintext, plaintext.length + 1);
            content[plaintext.length] = 0x02;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"),
                    new GCMParameterSpec(128, keys.nonce()));
            byte[] ciphertext = cipher.doFinal(content); // 16바이트 태그 포함

            // RFC 8188 헤더: salt(16) || rs(4, BE) || idlen(1) || keyid(=as_public 65)
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.writeBytes(salt);
            body.writeBytes(new byte[] {
                    (byte) (RECORD_SIZE >>> 24), (byte) (RECORD_SIZE >>> 16),
                    (byte) (RECORD_SIZE >>> 8), (byte) RECORD_SIZE });
            body.write(asPublicRaw.length);
            body.writeBytes(asPublicRaw);
            body.writeBytes(ciphertext);
            return body.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push 암호화 실패", e);
        }
    }

    /** RFC 8291 §3.4 키 유도. 반환값(CEK/NONCE)을 RFC 8291 §5 벡터로 검증한다. */
    DerivedKeys deriveKeys(byte[] ecdhSecret, byte[] authSecret, byte[] salt,
                           byte[] uaPublicRaw, byte[] asPublicRaw) {
        // IKM: HKDF-Extract(salt=auth_secret, ikm=ecdh) → Expand(key_info, 32)
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat(KEY_INFO_PREFIX, uaPublicRaw, asPublicRaw);
        byte[] ikm = expand(prkKey, keyInfo, 32);
        // 콘텐츠 키: HKDF-Extract(salt=record salt, ikm=IKM) → Expand
        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, CEK_INFO, 16);
        byte[] nonce = expand(prk, NONCE_INFO, 12);
        return new DerivedKeys(cek, nonce);
    }

    static byte[] ecdh(ECPrivateKey priv, ECPublicKey pub) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(priv);
        ka.doPhase(pub, true);
        return leftPad(ka.generateSecret(), 32); // P-256 X 좌표, 32바이트 고정
    }

    // ===== HKDF (HMAC-SHA-256) =====

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** HKDF-Expand 단일 블록(length ≤ 32): HMAC(prk, info || 0x01)[0..length-1]. */
    private static byte[] expand(byte[] prk, byte[] info, int length) {
        byte[] t = hmac(prk, concat(info, new byte[] { 0x01 }));
        return Arrays.copyOf(t, length);
    }

    // ===== EC point ↔ raw 65바이트 (0x04 || X32 || Y32) =====

    static ECPublicKey toPublicKey(byte[] raw) {
        if (raw.length != 65 || raw[0] != 0x04) {
            throw new IllegalArgumentException("uncompressed P-256 공개키(65바이트)가 아님");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("공개키 파싱 실패", e);
        }
    }

    static byte[] toRawPublic(ECPublicKey key) {
        ECPoint w = key.getW();
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(leftPad(w.getAffineX().toByteArray(), 32), 0, out, 1, 32);
        System.arraycopy(leftPad(w.getAffineY().toByteArray(), 32), 0, out, 33, 32);
        return out;
    }

    static ECPrivateKey toPrivateKey(byte[] rawScalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, rawScalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("개인키 파싱 실패", e);
        }
    }

    private static ECParameterSpec p256Params() {
        try {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(new ECGenParameterSpec("secp256r1"));
            return ap.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 파라미터 로드 실패", e);
        }
    }

    // ===== 바이트 유틸 =====

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    /** BigInteger.toByteArray() 의 부호 바이트/짧은 길이를 정리해 고정 길이 big-endian 으로. */
    static byte[] leftPad(byte[] b, int size) {
        if (b.length == size) {
            return b;
        }
        byte[] out = new byte[size];
        if (b.length > size) {
            System.arraycopy(b, b.length - size, out, 0, size);
        } else {
            System.arraycopy(b, 0, out, size - b.length, b.length);
        }
        return out;
    }
}
