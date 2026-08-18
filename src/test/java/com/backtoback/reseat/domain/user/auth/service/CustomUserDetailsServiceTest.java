package com.backtoback.reseat.domain.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.common.BaseUnitTest;

class CustomUserDetailsServiceTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("존재하는 이메일로 사용자 정보를 조회하면 UserDetails 객체를 반환한다")
    void loadUserByUsername_Success() {
        // given
        String email = "test@example.com";
        User user
            = User.builder().id(1L).email(email).name("홍길동").role(UserRole.USER).status(UserStatus.ACTIVE).build();

        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(email);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 조회 시 UsernameNotFoundException이 발생한다")
    void loadUserByUsername_NotFound_ThrowsException() {
        // given
        String email = "unknown@example.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(email))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("해당 이메일을 가진 사용자를 찾을 수 없습니다: " + email);
    }
}
