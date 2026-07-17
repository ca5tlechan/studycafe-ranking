package com.studycafe.ranking.user.dto;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import java.time.Instant;

/**
 * schoolId/schoolName 은 무소속이면 null.
 * warningCount/penalized 는 현재 스터디-월 기준(§3.6c) — 프로필·마이페이지 경고 표시용.
 */
public record UserResponse(
        Long id,
        String loginId,
        String displayName,
        int nameSeq,
        Long schoolId,
        String schoolName,
        int warningCount,
        boolean penalized
) {
    /** 주의: user.getSchool() 접근이 있으므로 트랜잭션(또는 school fetch된) 컨텍스트에서 호출할 것. */
    public static UserResponse from(User user) {
        School school = user.getSchool();
        int currentYm = StudyClock.studyMonthYm(Instant.now());
        int warnings = user.effectiveWarnings(currentYm);
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getDisplayName(),
                user.getNameSeq(),
                school != null ? school.getId() : null,
                school != null ? school.getName() : null,
                warnings,
                warnings >= StudyTimePolicy.PENALTY_THRESHOLD
        );
    }
}
