package com.backtoback.reseat.domain.user.auth.controller;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.service.AuthService;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestBody ReissueRequest request) {
        TokenResponse response = authService.reissue(request);
        return ResponseEntity.ok(response);
    }
}

