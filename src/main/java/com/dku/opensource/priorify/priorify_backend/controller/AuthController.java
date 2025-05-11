package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.UserService;
import com.dku.opensource.priorify.priorify_backend.dto.LoginRequest;
import com.dku.opensource.priorify.priorify_backend.dto.SignUpRequest;
import com.dku.opensource.priorify.priorify_backend.dto.UserResponseDto;
import com.dku.opensource.priorify.priorify_backend.dto.GoogleLoginRequest;
import com.dku.opensource.priorify.priorify_backend.model.User;
import com.dku.opensource.priorify.priorify_backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        // 사용자 이름이 이미 사용 중인지 확인
        if (userService.findByName(signUpRequest.getName()) != null) {
            return ResponseEntity.badRequest().body("Username is already taken!");
        }

        // 새 사용자 생성
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        
        userService.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        User user = userService.findByName(loginRequest.getName());
        
        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getName());
        
        // 응답 헤더에 JWT 토큰 추가
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", token);
        responseBody.put("user", UserResponseDto.builder()
                .userId(user.getId().toHexString())
                .name(user.getName())
                .message("Login successful")
                .build());
        
        return ResponseEntity.ok().headers(headers).body(responseBody);
    }

    @PostMapping("/google")
    public ResponseEntity<?> authenticateGoogleUser(@Valid @RequestBody GoogleLoginRequest googleLoginRequest) {
        try {
            // 구글 ID 토큰 검증 API 호출
            String googleTokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + googleLoginRequest.getIdToken();
            
            Map<String, Object> tokenInfo = restTemplate.getForObject(googleTokenInfoUrl, Map.class);
            
            if (tokenInfo != null && googleClientId.equals(tokenInfo.get("aud"))) {
                // 토큰에서 필요한 정보만 추출
                String googleId = (String) tokenInfo.get("sub");
                String email = (String) tokenInfo.get("email");
                String name = (String) tokenInfo.get("name");
                
                // 이미 존재하는 구글 사용자인지 확인
                User user = userService.findByGoogleId(googleId);
                
                if (user == null) {
                    // 새 사용자 생성 - 이메일과 이름만 저장
                    user = new User();
                    user.setGoogleId(googleId);
                    user.setEmail(email);
                    user.setName(email.substring(0, email.indexOf('@'))); // 이메일 주소에서 사용자 이름 추출
                    user.setDisplayName(name);
                    // 랜덤 비밀번호 생성
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    
                    userService.save(user);
                    log.info("새 구글 사용자 등록: {}", email);
                }
                
                String token = jwtTokenProvider.generateToken(user.getName());
                
                // 응답 헤더에 JWT 토큰 추가
                HttpHeaders headers = new HttpHeaders();
                headers.add("Authorization", "Bearer " + token);
                
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("token", token);
                // 사용자 정보 반환
                responseBody.put("user", UserResponseDto.builder()
                        .userId(user.getId().toHexString())
                        .name(user.getName())
                        .message("Google login successful")
                        .build());
                
                return ResponseEntity.ok().headers(headers).body(responseBody);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 ID 토큰입니다");
            }
        } catch (Exception e) {
            log.error("구글 로그인 실패: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("인증 실패: " + e.getMessage());
        }
    }
    
    @GetMapping("/oauth2/callback/google")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam("code") String code) {
        try {
            log.info("구글 OAuth 콜백 요청 처리: {}", code);
            
            // 1. 인증 코드로 액세스 토큰 요청
            String googleTokenUrl = "https://oauth2.googleapis.com/token";
            
            MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
            tokenRequest.add("client_id", googleClientId);
            tokenRequest.add("client_secret", googleClientSecret);
            tokenRequest.add("code", code);
            tokenRequest.add("grant_type", "authorization_code");
            tokenRequest.add("redirect_uri", "http://localhost:3000/auth/callback"); // TODO: - 배포시 URL 수정
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(tokenRequest, headers);
            
            ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                googleTokenUrl,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // 2. 액세스 토큰으로 사용자 정보 요청
            String accessToken = (String) tokenResponse.getBody().get("access_token");
            String idToken = (String) tokenResponse.getBody().get("id_token");
            
            if (accessToken == null || idToken == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("액세스 토큰 획득 실패");
            }
            
            // ID 토큰에서 사용자 정보 추출
            String googleTokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            Map<String, Object> tokenInfo = restTemplate.getForObject(googleTokenInfoUrl, Map.class);
            
            if (tokenInfo == null || !googleClientId.equals(tokenInfo.get("aud"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 ID 토큰");
            }
            
            // 3. 사용자 정보 확인 및 저장
            String googleId = (String) tokenInfo.get("sub");
            String email = (String) tokenInfo.get("email");
            String name = (String) tokenInfo.get("name");
            
            // 4. DB에서 사용자 확인 또는 새로 생성
            User user = userService.findByGoogleId(googleId);
            
            if (user == null) {
                user = new User();
                user.setGoogleId(googleId);
                user.setEmail(email);
                user.setName(email.substring(0, email.indexOf('@'))); // 이메일 주소에서 사용자 이름 추출
                user.setDisplayName(name);
                // 랜덤 비밀번호 생성
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                
                userService.save(user);
                log.info("새 구글 사용자 등록: {}", email);
            }
            
            // 5. JWT 토큰 생성
            String token = jwtTokenProvider.generateToken(user.getName());
            
            // 6. HTTP 응답 헤더에 토큰 추가
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("Authorization", "Bearer " + token);
            
            // 7. 응답 본문 구성
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("token", token);
            responseBody.put("user", UserResponseDto.builder()
                    .userId(user.getId().toHexString())
                    .name(user.getName())
                    .message("Google login successful")
                    .build());
            
            // 8. 리다이렉트 없이 JSON 응답 반환
            return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(responseBody);
            
        } catch (Exception e) {
            log.error("인증 콜백 처리 실패: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("인증 실패: " + e.getMessage());
        }
    }
} 