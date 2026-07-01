package com.backtoback.reseat.domain.user.repository;

import com.backtoback.reseat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository {
    //이메일로 유저 정보 조회 (로그인 시 사용)
    Optional<User> findByEmail(String email);

    //이메일 중복 여부 확인 (회원가입 시 검증용)
    boolean existsByEmail(String email);

    //닉네임 중복 여부 확인
    Boolean existsByNickname(String nickname);

}
