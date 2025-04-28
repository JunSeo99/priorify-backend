package com.dku.opensource.priorify.priorify_backend;

import com.dku.opensource.priorify.priorify_backend.dto.LoginRequest;
import com.dku.opensource.priorify.priorify_backend.dto.SignUpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 API 테스트")
    void signupTest() throws Exception {
        SignUpRequest request = new SignUpRequest();
        request.setName("testuser");
        request.setPassword("testpass");
        request.setPasswordConfirm("testpass");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 API 테스트")
    void loginTest() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setName("testuser");
        request.setPassword("testpass");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("에러 컨트롤러 테스트")
    void errorControllerTest() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카테고리 통계 API 테스트")
    void analyticsCategoryTest() throws Exception {
        String start = LocalDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME);
        String end = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        mockMvc.perform(get("/api/analytics/categories")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk());
    }

    // 이외에도 각 컨트롤러별로 주요 엔드포인트에 대해 비슷하게 테스트를 추가할 수 있습니다.
}