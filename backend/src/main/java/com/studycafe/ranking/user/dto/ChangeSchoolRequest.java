package com.studycafe.ranking.user.dto;

/** 본인 소속(학교) 변경 요청. schoolId=null 이면 무소속(§3.7). */
public record ChangeSchoolRequest(Long schoolId) {
}
