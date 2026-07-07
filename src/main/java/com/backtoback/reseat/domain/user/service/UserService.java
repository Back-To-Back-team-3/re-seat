package com.backtoback.reseat.domain.user.service;

import com.backtoback.reseat.domain.user.dto.request.UserSignUpRequest;
import com.backtoback.reseat.domain.user.dto.response.UserSignUpResponse;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.DuplicateEmailException;
import com.backtoback.reseat.domain.user.exception.DuplicatePhoneException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; //BCryptPasswordEncoder가 주입

    @Transactional
    public UserSignUpResponse signUp(UserSignUpRequest request){
       //1. 이메일 중복 검증
        if(userRepository.existsByEmail(request.getEmail())){
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        }

        //1-2. 전화번호 중복 검증
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicatePhoneException("이미 사용 중인 전화번호입니다.");
        }

        //2. 비밀번호 암호화
        String encodePassword = passwordEncoder.encode(request.getPassword());

        //3. Request DTO 를 User엔티티로 변환
        User user = User.builder()
                .email(request.getEmail())
                .password(encodePassword)
                .name(request.getName())
                .phone(request.getPhone())
                .build();

        //4.데이터베이스 저장 및 고유 식별자 반환
         User savedUser = userRepository.save(user);
         return UserSignUpResponse.from(savedUser);


    }
}
