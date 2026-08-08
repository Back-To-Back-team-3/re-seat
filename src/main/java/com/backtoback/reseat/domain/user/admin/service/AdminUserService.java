package com.backtoback.reseat.domain.user.admin.service;

import com.backtoback.reseat.domain.user.admin.dto.request.UserSearchCondition;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminUserResponse;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // 1. 회원 전체 조회 (페이징 + 필터링)
    public Page<AdminUserResponse> searchUsers(UserSearchCondition condition, Pageable pageable) {
        return userRepository.searchUsers(condition, pageable)
            .map(AdminUserResponse::from);
    }

    // 2. 회원 상세 조회
    public AdminUserResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        return AdminUserResponse.from(user);
    }

    // 3. 회원 권한 변경
    @Transactional
    public void updateUserRole(Long userId, UserRole role) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "탈퇴한 회원의 권한은 변경할 수 없습니다.");
        }

        user.updateRole(role);
    }

    // 4. 회원 상태 변경
    @Transactional
    public void updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 탈퇴한 회원입니다.");
        }

        if (status == UserStatus.DELETED) {
            user.withdraw();
            refreshTokenRepository.findByUser(user)
                .ifPresent(refreshTokenRepository::delete);
        } else {
            user.updateStatus(status);
        }
    }
}
