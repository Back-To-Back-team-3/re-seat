package com.backtoback.reseat.domain.user.auth.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final String redirectUrl;

    public OAuth2AuthenticationFailureHandler(
        @Value("${oauth2.redirect.frontend-url:http://localhost:5173}") String redirectUrl
    ) {
        this.redirectUrl = redirectUrl;
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    )
        throws IOException,
        ServletException {

        String targetUrl
            = UriComponentsBuilder
                .fromUriString(redirectUrl)
                .queryParam("error", exception.getLocalizedMessage())
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
