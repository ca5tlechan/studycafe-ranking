package com.studycafe.ranking.session;

import com.studycafe.ranking.session.dto.CurrentSessionResponse;
import com.studycafe.ranking.session.dto.SessionToggleResponse;
import com.studycafe.ranking.session.dto.ToggleRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** QR 토글: 활성 세션 있으면 체크아웃, 없으면 체크인. */
    @PostMapping("/toggle")
    public SessionToggleResponse toggle(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody ToggleRequest request) {
        return sessionService.toggle(userId, request.cafeToken());
    }

    /** 현재 활성 세션(있으면 "HH:MM부터 공부 중" 표시용). */
    @GetMapping("/current")
    public CurrentSessionResponse current(@AuthenticationPrincipal Long userId) {
        return sessionService.current(userId);
    }
}
