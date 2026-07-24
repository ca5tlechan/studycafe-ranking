package com.studycafe.ranking.admin.dto;

import com.studycafe.ranking.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 관리자 API 요청/응답 DTO 모음. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 사용자 목록 한 줄. 관리자는 마스킹 없는 실명을 본다(운영 식별용). */
    public record AdminUser(
            Long id,
            String loginId,
            String displayName,
            Long schoolId,
            String schoolName,
            Role role,
            int warningCount,
            boolean penalized,
            boolean checkedIn
    ) {
    }

    public record RoleUpdateRequest(@NotNull Role role) {
    }

    /** 유저 소속 변경 요청. schoolId=null 이면 무소속으로 이동. */
    public record UserSchoolRequest(Long schoolId) {
    }

    public record SchoolRequest(@NotBlank String name, String shortName) {
    }

    public record AdminSchool(Long id, String name, String shortName, int memberCount) {
    }

    /** QR 재발급 결과 — 새 토큰(QR 재출력·부착 필요). */
    public record CafeQr(Long id, String name, String qrToken) {
    }

    /** 04:00 배치 수동 실행 결과. */
    public record BatchResult(int closed) {
    }
}
