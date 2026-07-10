package com.studycafe.ranking.school.dto;

import com.studycafe.ranking.domain.School;

public record SchoolResponse(Long id, String name, String shortName) {

    public static SchoolResponse from(School school) {
        // §3.3: shortName 이 없으면 name 으로 폴백(랭킹 표기 계약 유지)
        String shortName = school.getShortName() != null ? school.getShortName() : school.getName();
        return new SchoolResponse(school.getId(), school.getName(), shortName);
    }
}
