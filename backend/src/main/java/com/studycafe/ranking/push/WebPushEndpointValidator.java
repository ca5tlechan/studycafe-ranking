package com.studycafe.ranking.push;

import com.studycafe.ranking.common.exception.InvalidPushEndpointException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * 구독 endpoint SSRF 방어(§6). 인증 사용자가 임의 endpoint 를 저장하면 전송기가 그 주소로 서버에서
 * POST 하므로, 저장 전에 <b>https 스킴 + 공인(내부/사설 아님) 호스트</b>만 허용한다.
 * <p>DNS rebinding 완전 차단은 애플리케이션 레벨로는 불가능하며(배포 시 네트워크 egress 정책으로 방어,
 * CLAUDE.md §12) 여기선 명백한 로컬/사설 대상만 거른다. 실 푸시 서비스(FCM/Mozilla/WNS 등)는
 * 모두 공인 https 도메인이므로 정상 구독은 통과한다.
 */
public final class WebPushEndpointValidator {

    private WebPushEndpointValidator() {
    }

    public static void validate(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new InvalidPushEndpointException("구독 주소 형식이 올바르지 않습니다.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidPushEndpointException("구독 주소는 https 여야 합니다.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidPushEndpointException("구독 주소에 호스트가 없습니다.");
        }
        // 대괄호 IPv6 표기 정리.
        String bareHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (bareHost.equalsIgnoreCase("localhost")
                || bareHost.endsWith(".localhost")
                || bareHost.endsWith(".local")) {
            throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
        }
        // 호스트가 IP 리터럴이면 사설/로컬 대역을 차단한다. 호스트명(도메인)은 여기서 DNS 조회하지 않는다
        // (조회 자체가 rebinding 표면이 되므로 — 네트워크 egress 정책이 담당, §12).
        if (isIpLiteral(bareHost)) {
            InetAddress addr;
            try {
                addr = InetAddress.getByName(bareHost); // 리터럴이라 DNS 조회 없음
            } catch (UnknownHostException e) {
                throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
            }
            if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isMulticastAddress() || isUniqueLocalIpv6(addr)) {
                throw new InvalidPushEndpointException("허용되지 않는 구독 주소입니다.");
            }
        }
    }

    /** 점표기 IPv4 또는 콜론이 있는 IPv6 만 리터럴로 본다(도메인명은 제외). */
    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true; // IPv6
        }
        // IPv4: 네 옥텟 모두 숫자
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) {
                return false;
            }
            for (int i = 0; i < p.length(); i++) {
                if (!Character.isDigit(p.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** IPv6 ULA(fc00::/7) — Java 의 isSiteLocalAddress 는 IPv6 ULA 를 포함하지 않는다. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] a = addr.getAddress();
        return a.length == 16 && (a[0] & 0xFE) == 0xFC;
    }
}
