package com.backtoback.reseat.domain.user.admin.dto.request;

import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;

public record UserSearchCondition(
    String email,
    String name,
    String nickname,
    String phone,
    UserRole role,
    UserStatus status
) {}
