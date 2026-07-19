package com.studycafe.ranking.push;

import com.studycafe.ranking.common.exception.InvalidPushEndpointException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushEndpointValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc123",
            "https://updates.push.services.mozilla.com/wpush/v2/xyz",
            "https://sea1.notify.windows.com/w/?token=abc",
    })
    void acceptsPublicHttpsPushEndpoints(String endpoint) {
        assertThatCode(() -> WebPushEndpointValidator.validate(endpoint)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/x",      // https 아님
            "ftp://fcm.googleapis.com/x",       // https 아님
            "https://localhost/x",              // 로컬
            "https://foo.local/x",              // mDNS 로컬
            "https://127.0.0.1/x",              // loopback
            "https://10.1.2.3/x",               // 사설
            "https://192.168.0.5/x",            // 사설
            "https://172.16.9.9/x",             // 사설
            "https://169.254.10.10/x",          // link-local
            "https://[::1]/x",                  // IPv6 loopback
            "https://[fd00::1]/x",              // IPv6 ULA
            "https://100.64.0.1/x",             // 공유대역 CGNAT (100.64/10)
            "https://100.127.255.254/x",        // 공유대역 CGNAT 상단
            "https://2130706433/x",             // 우회: 십진 정수 = 127.0.0.1
            "https://0x7f000001/x",             // 우회: 16진 = 127.0.0.1
            "https://017700000001/x",           // 우회: 8진 = 127.0.0.1
            "https://127.1/x",                  // 우회: 축약 = 127.0.0.1
            "https://0177.0.0.1/x",             // 우회: 선행 0 옥텟
            "not a url",                        // 형식 오류
    })
    void rejectsNonHttpsOrInternalEndpoints(String endpoint) {
        assertThatThrownBy(() -> WebPushEndpointValidator.validate(endpoint))
                .isInstanceOf(InvalidPushEndpointException.class);
    }
}
