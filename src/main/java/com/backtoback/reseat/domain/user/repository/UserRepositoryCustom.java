package com.backtoback.reseat.domain.user.repository;

import com.backtoback.reseat.domain.user.admin.dto.request.UserSearchCondition;
import com.backtoback.reseat.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {
    Page<User> searchUsers(UserSearchCondition condition, Pageable pageable);
}
