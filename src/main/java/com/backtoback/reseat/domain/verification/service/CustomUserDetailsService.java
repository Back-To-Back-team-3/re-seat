package com.backtoback.reseat.domain.user.verification.service;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        //이메일 기반으로 유저 엔티티를 찾은 뒤, 시큐리티 규격
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new UsernameNotFoundException("해당 이메일을 가진 사용자를 찾을 수 없습니다" + email));

        return new CustomUserDetails(user);
    }

}
