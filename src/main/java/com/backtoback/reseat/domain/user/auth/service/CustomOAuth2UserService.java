package com.backtoback.reseat.domain.user.auth.service;

import com.backtoback.reseat.domain.user.auth.dto.CustomOAuth2User;
import com.backtoback.reseat.domain.user.auth.dto.KakaoOAuth2UserInfo;
import com.backtoback.reseat.domain.user.auth.dto.OAuth2UserInfo;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuth2UserInfo oAuth2UserInfo;
        if ("kakao".equalsIgnoreCase(registrationId)) {
            oAuth2UserInfo = new KakaoOAuth2UserInfo(attributes);
        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }

        String provider = oAuth2UserInfo.getProvider();
        String providerId = oAuth2UserInfo.getProviderId();
        String email = oAuth2UserInfo.getEmail();
        String name = oAuth2UserInfo.getName();

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existingUser -> {
                    if (existingUser.getStatus() == UserStatus.DELETED) {
                        throw new OAuth2AuthenticationException("탈퇴한 사용자입니다.");
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    if (email != null) {
                        return userRepository.findByEmail(email)
                                .map(existingUser -> {
                                    if (existingUser.getStatus() == UserStatus.DELETED) {
                                        throw new OAuth2AuthenticationException("탈퇴한 사용자입니다.");
                                    }
                                    existingUser.updateSocialInfo(provider, providerId);
                                    return userRepository.save(existingUser);
                                })
                                .orElseGet(() -> createNewOAuthUser(email, name, provider, providerId));
                    }
                    return createNewOAuthUser(null, name, provider, providerId);
                });

        return new CustomOAuth2User(user, attributes);
    }

    private User createNewOAuthUser(String email, String name, String provider, String providerId) {
        UserRole role = "admin@reseat.com".equals(email) ? UserRole.ADMIN : UserRole.USER;
        User newUser = User.builder()
                .email(email != null ? email : providerId + "@kakao.com")
                .name(name != null ? name : "카카오 사용자")
                .provider(provider)
                .providerId(providerId)
                .role(role)
                .status(UserStatus.ACTIVE)
                .isVerified(false)
                .build();
        return userRepository.save(newUser);
    }
}
