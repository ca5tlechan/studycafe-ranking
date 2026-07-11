package com.studycafe.ranking.auth;

import com.studycafe.ranking.auth.dto.LoginRequest;
import com.studycafe.ranking.auth.dto.LoginResponse;
import com.studycafe.ranking.auth.dto.SignupRequest;
import com.studycafe.ranking.common.exception.DuplicateLoginIdException;
import com.studycafe.ranking.common.exception.InvalidCredentialsException;
import com.studycafe.ranking.common.exception.SchoolNotFoundException;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /** 로그인 타이밍 사이드채널 방어용 더미 해시(존재하지 않는 아이디에도 동일하게 matches 수행). */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       SchoolRepository schoolRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.dummyPasswordHash = passwordEncoder.encode("timing-defense-dummy-password");
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new DuplicateLoginIdException(request.loginId());
        }
        School school = resolveSchool(request.schoolId());
        int nameSeq = nextNameSeq(request.displayName(), school);
        User user = new User(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                nameSeq,
                school
        );
        try {
            return UserResponse.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // existsByLoginId 이후 동시 가입 레이스로 loginId 유니크 제약 충돌 → 중복 아이디(409)로 변환
            throw new DuplicateLoginIdException(request.loginId());
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 아이디가 없어도 더미 해시로 matches 를 수행해 응답 시간을 균일화(loginId 열거 방지).
        User user = userRepository.findByLoginId(request.loginId()).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean matches = passwordEncoder.matches(request.password(), hashToCheck);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        String token = tokenProvider.createToken(user.getId());
        return LoginResponse.bearer(token, UserResponse.from(user));
    }

    private School resolveSchool(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
    }

    /** 같은 (displayName, school) 조합의 기존 인원 + 1. 무소속(null)도 같은 규칙. §3.3 */
    private int nextNameSeq(String displayName, School school) {
        int existing = (school == null)
                ? userRepository.countByDisplayNameAndSchoolIsNull(displayName)
                : userRepository.countByDisplayNameAndSchool(displayName, school);
        return existing + 1;
    }
}
