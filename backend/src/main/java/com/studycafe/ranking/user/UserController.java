package com.studycafe.ranking.user;

import com.studycafe.ranking.user.dto.ChangeSchoolRequest;
import com.studycafe.ranking.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 현재 로그인 사용자 정보. principal(userId)은 JwtAuthenticationFilter가 세팅. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getById(userId);
    }

    /** 본인 소속(학교) 변경. body.schoolId=null 이면 무소속. 전학·진학 대응(관리자 변경과 동일 로직). */
    @PutMapping("/me/school")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMySchool(@AuthenticationPrincipal Long userId, @RequestBody ChangeSchoolRequest req) {
        userService.changeSchool(userId, req.schoolId());
    }
}
