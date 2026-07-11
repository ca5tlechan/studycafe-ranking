package com.studycafe.ranking.auth.dto;

import com.studycafe.ranking.user.dto.UserResponse;

public record LoginResponse(
        String token,
        String tokenType,
        UserResponse user
) {
    public static LoginResponse bearer(String token, UserResponse user) {
        return new LoginResponse(token, "Bearer", user);
    }
}
