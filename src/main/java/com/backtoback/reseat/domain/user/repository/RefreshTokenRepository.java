package com.backtoback.reseat.domain.user.repository;

import com.backtoback.reseat.domain.user.entity.RefreshToken;
import com.backtoback.reseat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    void deleteByUser(User user);
}
