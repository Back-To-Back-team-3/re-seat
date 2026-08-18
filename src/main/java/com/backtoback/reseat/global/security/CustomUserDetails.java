package com.backtoback.reseat.global.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.backtoback.reseat.domain.user.entity.User;

public record CustomUserDetails(Long id, String email, String role) implements UserDetails {

    public static CustomUserDetails of(Long id, String email, String role) {
        return new CustomUserDetails(id, email, role);
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(
            user.getId(),
            user.getEmail(),
            user.getRole() != null ? user.getRole().name() : "USER"
        );
    }

    public CustomUserDetails(User user) {
        this(user.getId(), user.getEmail(), user.getRole() != null ? user.getRole().name() : "USER");
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return Collections.emptyList();
        }
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return Collections.singletonList(new SimpleGrantedAuthority(roleWithPrefix));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}
