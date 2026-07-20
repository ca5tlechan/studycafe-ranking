package com.studycafe.ranking.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SPA 폴백 결정 검증: 정적 파일이 없을 때 HTML 탐색 요청만 index.html 로, 나머지(자산·API)는 null(→404).
 * (locations 를 비워 super 가 항상 "못 찾음(null)"을 반환하게 하고, 폴백 분기만 검증한다.)
 */
class SpaResourceResolverTest {

    private final WebConfig.SpaResourceResolver resolver = new WebConfig.SpaResourceResolver();
    private final ResourceResolverChain chain = mock(ResourceResolverChain.class);

    private Resource resolve(String accept, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        return resolver.resolveResource(request, path, List.of(), chain);
    }

    @Test
    void htmlNavigationFallsBackToIndex() {
        Resource res = resolve("text/html,application/xhtml+xml", "my");
        assertThat(res).isNotNull();
        assertThat(res.getFilename()).isEqualTo("index.html");
    }

    @Test
    void missingStaticAssetReturnsNull() {
        // Accept 에 text/html 이 없는 자산 요청 → index.html 로 둔갑시키지 않고 404 로 남긴다.
        assertThat(resolve("*/*", "assets/missing.js")).isNull();
        assertThat(resolve("image/avif,image/webp,*/*", "favicon.ico")).isNull();
    }

    @Test
    void apiPathReturnsNull() {
        assertThat(resolve("text/html", "api")).isNull();
        assertThat(resolve("text/html", "api/users/me")).isNull();
    }

    @Test
    void missingAcceptHeaderReturnsNull() {
        assertThat(resolve(null, "my")).isNull();
    }
}
