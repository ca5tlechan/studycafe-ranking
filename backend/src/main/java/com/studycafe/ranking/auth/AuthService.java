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
import org.hibernate.exception.ConstraintViolationException;
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
            // saveAndFlush: 커밋이 아니라 이 시점에 제약 위반을 즉시 유발해 잡는다.
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // existsByLoginId 이후 동시 가입 레이스. loginId 유니크 충돌만 409로 변환하고, 그 외 제약 위반은 전파.
            if (isLoginIdConflict(e)) {
                throw new DuplicateLoginIdException(request.loginId(), e);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 아이디가 없어도 더미 해시로 matches 를 수행해 응답 시간을 균일화(loginId 열거 방지).
        User user = userRepository.findByLoginIdWithSchool(request.loginId()).orElse(null);
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

    /**
     * DataIntegrityViolationException 이 loginId 유니크 제약(uk_users_login_id) 위반인지 판별.
     * 원인 체인에서 Hibernate ConstraintViolationException 을 찾아 제약명으로만 판단한다.
     * (메시지 문자열 매칭은 NOT NULL/길이 초과 등 다른 위반을 오탐할 수 있어 사용하지 않는다.)
     */
    private boolean isLoginIdConflict(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.toLowerCase().contains("uk_users_login_id");
            }
        }
        return false;
    }
}
