package com.backtoback.reseat.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.dto.request.PasswordChangeRequest;
import com.backtoback.reseat.domain.user.dto.request.UserSignUpRequest;
import com.backtoback.reseat.domain.user.dto.request.UserUpdateRequest;
import com.backtoback.reseat.domain.user.dto.response.UserProfileResponse;
import com.backtoback.reseat.domain.user.dto.response.UserSignUpResponse;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.exception.DuplicateEmailException;
import com.backtoback.reseat.domain.user.exception.DuplicatePhoneException;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder가 주입
    private final RefreshTokenRepository refreshTokenRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public UserSignUpResponse signUp(UserSignUpRequest request) {
        // 1. 이메일 중복 검증
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        }

        // 1-2. 전화번호 중복 검증
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicatePhoneException("이미 사용 중인 전화번호입니다.");
        }

        // 2. 비밀번호 암호화
        String encodePassword = passwordEncoder.encode(request.getPassword());

        // 3. Request DTO 를 User엔티티로 변환
        User user
            = User
                .builder()
                .email(request.getEmail())
                .password(encodePassword)
                .name(request.getName())
                .phone(request.getPhone())
                .build();

        // 4. 데이터베이스 저장 및 고유 식별자 반환
        User savedUser = userRepository.save(user);
        return UserSignUpResponse.from(savedUser);
    }

    // 내 정보 조회
    public UserProfileResponse getMyProfile(Long userId) {
        User user
            = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));

        return UserProfileResponse.from(user);
    }

    // 회원 정보 수정
    @Transactional
    public void updateProfile(Long userId, UserUpdateRequest request) {
        User user
            = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));

        if (!user.getPhone().equals(request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicatePhoneException(request.getPhone());
        }
        user.updateProfile(request.getName(), request.getPhone());
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user
            = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("현재 비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "새로운 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.changePassword(newEncodedPassword);
    }

    // 회원탈퇴 서비스 로직
    @Transactional
    public void withdraw(Long userId) {
        // 사용자 조회 및 404 예외 처리
        User user
            = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));

        // 이미 탈퇴한 회원인 경우 중복 처리 방지
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 탈퇴한 회원입니다.");
        }

        // 티켓 보유 여부 검증
        boolean hasActiveTicket = !ticketRepository.findByUserIdAndStatus(userId, TicketStatus.ISSUED).isEmpty();
        if (hasActiveTicket) {
            throw new BusinessException(ErrorCode.TICKET_EXISTS_ON_WITHDRAWAL, "정산 미완료 티켓이 존재하여 탈퇴할 수 없습니다.");
        }

        // 엔티티 메서드를 호출하여 상태 변경 및 개인정보 마스킹
        user.withdraw();

        // DB 내 저장되어 있는 사용자의 리프레시 토큰 제거
        refreshTokenRepository.findByUser(user).ifPresent(refreshTokenRepository::delete);
    }

}
