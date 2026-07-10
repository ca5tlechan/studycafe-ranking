package com.studycafe.ranking.school.dto;

import com.studycafe.ranking.domain.School;

public record SchoolResponse(Long id, String name, String shortName) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(school.getId(), school.getName(), school.getShortName());
    }
}
