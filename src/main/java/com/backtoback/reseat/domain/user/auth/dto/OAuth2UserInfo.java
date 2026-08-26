package com.backtoback.reseat.domain.user.auth.dto;

public interface OAuth2UserInfo {
    String getProviderId();

    String getProvider();

    String getEmail();

    String getName();
}
