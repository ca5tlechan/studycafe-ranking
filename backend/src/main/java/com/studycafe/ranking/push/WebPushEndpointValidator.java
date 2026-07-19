package com.studycafe.ranking.push;

import com.studycafe.ranking.common.exception.InvalidPushEndpointException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * 구독 endpoint SSRF 방어(§6). 인증 사용자가 임의 endpoint 를 저장하면 전송기가 그 주소로 서버에서
 * POST 하므로, 저장 전에 <b>https 스킴 + 공인(내부/사설 아님) 대상</b>만 허용한다.
 * <p>핵심: IP 주소는 <b>표준 점표기 4옥텟 십진 IPv4 또는 표준 IPv6 리터럴만</b> 받아들이고, 그 밖의
 * 숫자 표기(정수 {@code 2130706433}, 16진 {@code 0x7f000001}, 8진 {@code 017700000001}, 축약
 * {@code 127.1})는 우회 수단으로 보고 거부한다 — 이들은 플랫폼 resolver 가 loopback 등으로 해석하지만
 * 정상 푸시 서비스는 절대 쓰지 않는다. 허용된 IP 는 사설/loopback/link-local/멀티캐스트/ULA/공유대역
 * (100.64.0.0/10)을 범위로 차단한다.
 * <p>진짜 도메인명은 여기서 DNS 로 조회하지 않는다(조회 자체가 SSRF·rebinding 표면이 된다). 도메인이
 * 내부 IP 를 가리키는 케이스와 DNS rebinding(TOCTOU) 은 배포 시 네트워크 egress 정책이 담당한다(§12).
 */
public final class WebPushEndpointValidator {

    private WebPushEndpointValidator() {
    }

    public static void validate(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new InvalidPushEndpointException("구독 주소 형식이 올바르지 않습니다.", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidPushEndpointException("구독 주소는 https 여야 합니다.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidPushEndpointException("구독 주소에 호스트가 없습니다.");
        }
        String bareHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        String lower = bareHost.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local")) {
            throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
        }

        if (bareHost.indexOf(':') >= 0) {
            // IPv6 리터럴 — InetAddress 가 로컬에서 파싱(DNS 조회 없음).
            rejectIfInternal(parseLiteral(bareHost));
            return;
        }
        if (isNumericIpv4Candidate(bareHost)) {
            // 숫자로만 이뤄진 호스트는 IP 리터럴 시도다. 표준 점표기 4옥텟 십진만 허용하고,
            // 나머지 숫자 표기(정수/16진/8진/축약)는 우회로 간주해 거부한다.
            byte[] v4 = parseStrictDottedQuad(bareHost);
            if (v4 == null) {
                throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
            }
            rejectIfInternal(parseLiteral(bareHost)); // v4 != null 이면 표준 점표기라 리터럴 파싱이 안전
            return;
        }
        // 도메인명 — DNS 조회하지 않는다(§12). localhost/.local 은 위에서 이미 걸렀다.
    }

    private static InetAddress parseLiteral(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.", e);
        }
    }

    private static void rejectIfInternal(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                || addr.isMulticastAddress() || isUniqueLocalIpv6(addr) || isCarrierGradeNat(addr)) {
            throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
        }
    }

    /** 점(.)으로 나눈 모든 라벨이 숫자(십진/16진/8진)면 IP 리터럴 시도로 본다. 도메인은 알파벳 라벨을 가진다. */
    private static boolean isNumericIpv4Candidate(String host) {
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty()) {
                return false;
            }
            boolean hex = label.length() > 2 && (label.charAt(0) == '0') && (label.charAt(1) == 'x' || label.charAt(1) == 'X');
            if (hex) {
                for (int i = 2; i < label.length(); i++) {
                    if (Character.digit(label.charAt(i), 16) < 0) {
                        return false;
                    }
                }
            } else {
                for (int i = 0; i < label.length(); i++) {
                    if (!Character.isDigit(label.charAt(i))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 정확히 4개 십진 옥텟(각 0~255, 선행 0 금지)이면 4바이트로, 아니면 null. */
    private static byte[] parseStrictDottedQuad(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3 || (p.length() > 1 && p.charAt(0) == '0')) {
                return null; // 빈값·과길이·선행 0(8진 오해) 거부
            }
            int v = 0;
            for (int j = 0; j < p.length(); j++) {
                if (!Character.isDigit(p.charAt(j))) {
                    return null;
                }
                v = v * 10 + (p.charAt(j) - '0');
            }
            if (v > 255) {
                return null;
            }
            out[i] = (byte) v;
        }
        return out;
    }

    /** IPv6 ULA(fc00::/7) — Java 의 isSiteLocalAddress 는 IPv6 ULA 를 포함하지 않는다. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] a = addr.getAddress();
        return a.length == 16 && (a[0] & 0xFE) == 0xFC;
    }

    /** 공유 주소 대역 100.64.0.0/10 (RFC 6598 CGNAT) — isSiteLocalAddress 가 다루지 않는다. */
    private static boolean isCarrierGradeNat(InetAddress addr) {
        byte[] a = addr.getAddress();
        return a.length == 4 && (a[0] & 0xFF) == 100 && (a[1] & 0xFF) >= 64 && (a[1] & 0xFF) <= 127;
    }
}
