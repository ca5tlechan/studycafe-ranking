package com.studycafe.ranking.user.dto;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;

/** schoolId/schoolName 은 무소속이면 null. */
public record UserResponse(
        Long id,
        String loginId,
        String displayName,
        int nameSeq,
        Long schoolId,
        String schoolName
) {
    /** 주의: user.getSchool() 접근이 있으므로 트랜잭션(또는 school fetch된) 컨텍스트에서 호출할 것. */
    public static UserResponse from(User user) {
        School school = user.getSchool();
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getDisplayName(),
                user.getNameSeq(),
                school != null ? school.getId() : null,
                school != null ? school.getName() : null
        );
    }
}
