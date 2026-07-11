package com.studycafe.ranking.session.dto;

import jakarta.validation.constraints.NotBlank;

/** QR에서 추출한 카페 토큰. */
public record ToggleRequest(@NotBlank String cafeToken) {
}
