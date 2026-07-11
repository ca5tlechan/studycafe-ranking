package com.studycafe.ranking.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** schoolId == null → 무소속(§3.7). */
public record SignupRequest(
        @NotBlank @Size(min = 3, max = 30) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 20) String displayName,
        Long schoolId
) {
}
