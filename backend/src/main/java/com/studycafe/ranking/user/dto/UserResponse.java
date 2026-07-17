package com.studycafe.ranking.user.dto;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import java.time.Instant;

/**
 * schoolId/schoolName 은 무소속이면 null.
 * warningCount/penalized 는 현재 스터디-월 기준(§3.6c) — 프로필·마이페이지 경고 표시용.
 * penaltyThreshold 를 함께 내려줘, 프론트가 StudyTimePolicy.PENALTY_THRESHOLD 값을
 * 별도로 하드코딩하지 않게 한다(정책 값이 바뀌면 여기 하나만 바뀌면 됨).
 */
public record UserResponse(
        Long id,
        String loginId,
        String displayName,
        int nameSeq,
        Long schoolId,
        String schoolName,
        int warningCount,
        boolean penalized,
        int penaltyThreshold
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
                warnings >= StudyTimePolicy.PENALTY_THRESHOLD,
                StudyTimePolicy.PENALTY_THRESHOLD
        );
    }
}
