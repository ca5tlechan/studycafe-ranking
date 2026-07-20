package com.studycafe.ranking.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.util.List;

/**
 * 단일 오리진 배포: Spring 이 빌드된 프론트(정적 PWA)를 함께 서빙한다.
 * <p>정적 파일이 있으면 그대로 주고, 없으면(클라이언트 라우트 /my·/admin 등) SPA 진입점 index.html 로
 * 폴백한다. 단 {@code /api} 및 {@code /api/**} 는 폴백하지 않는다 — 컨트롤러가 처리하거나 404 로 남는다
 * (폴백하면 API 404 가 index.html 로 둔갑한다). API 핸들러는 정적 리소스 핸들러보다 먼저 매칭되므로
 * 정상 API 는 영향받지 않는다.
 */
@Component
public class WebConfig implements WebMvcConfigurer {

    private static final Resource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * 정적 파일이 있으면 그대로, 없으면 SPA 진입점(index.html)로 폴백. API 경로는 폴백에서 제외한다.
     * <p>public {@code resolveResource} 를 오버라이드한다 — protected {@code getResource} 는 Spring 7 에서
     * {@code forRemoval=true} 로 deprecated 되어 제거 시 컴파일이 깨지기 때문. 정상 해석은 super 에 위임하고
     * (보안 검사 포함), 못 찾은 경우에만 폴백한다.
     */
    static class SpaResourceResolver extends PathResourceResolver {
        @Override
        public Resource resolveResource(HttpServletRequest request, String requestPath,
                                        List<? extends Resource> locations, ResourceResolverChain chain) {
            Resource resolved = super.resolveResource(request, requestPath, locations, chain);
            if (resolved != null) {
                return resolved;
            }
            // 정확히 "api" 또는 "api/..." 는 SPA 폴백 대상이 아니다(컨트롤러 처리/404 로 남긴다).
            if (requestPath.equals("api") || requestPath.startsWith("api/")) {
                return null;
            }
            return INDEX;
        }
    }

    /**
     * .webmanifest 를 application/manifest+json 으로 서빙한다(기본은 octet-stream). PWA 설치 조건에서
     * 매니페스트 MIME 을 따지는 브라우저(특히 iOS Safari)를 위해 — 설치형 PWA 는 iOS Web Push 의 전제다(§8.4).
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMimeCustomizer() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}

