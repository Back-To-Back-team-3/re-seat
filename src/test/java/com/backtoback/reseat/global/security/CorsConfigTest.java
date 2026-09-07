package com.backtoback.reseat.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용된 Origin(http://localhost:3000)으로 Preflight 요청 시 CORS 헤더가 반환된다")
    void should_allowCors_when_allowedOrigin3000() throws Exception {
        mockMvc
            .perform(
                options("/api/v1/games")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용된 Origin(https://re-seat.netlify.app)으로 Preflight 요청 시 CORS 헤더가 반환된다")
    void should_allowCors_when_allowedOriginNetlify() throws Exception {
        mockMvc
            .perform(
                options("/api/v1/games")
                    .header(HttpHeaders.ORIGIN, "https://re-seat.netlify.app")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://re-seat.netlify.app"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용되지 않은 Origin으로 Preflight 요청 시 403 Forbidden과 함께 Access-Control-Allow-Origin 헤더가 반환되지 않는다")
    void should_notAllowCors_when_disallowedOrigin() throws Exception {
        mockMvc
            .perform(
                options("/api/v1/games")
                    .header(HttpHeaders.ORIGIN, "http://untrusted-domain.com")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            )
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
