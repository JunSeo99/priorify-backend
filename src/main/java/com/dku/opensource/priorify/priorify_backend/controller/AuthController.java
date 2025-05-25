package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.GoogleAPIService;
import com.dku.opensource.priorify.priorify_backend.service.UserService;
import com.dku.opensource.priorify.priorify_backend.dto.UserResponseDto;
import com.dku.opensource.priorify.priorify_backend.dto.CalendarSyncResultDto;
import com.dku.opensource.priorify.priorify_backend.dto.GoogleLoginRequest;
import com.dku.opensource.priorify.priorify_backend.model.User;
import com.dku.opensource.priorify.priorify_backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

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
    
    @Autowired
    private GoogleAPIService googleAPIService;

    // 구글 로그인으로 처리하기 때문에 기존 로그인/회원가입 제거

    // 구글 로그인 처리
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
                    user.setName(name);
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
    public Single<ResponseEntity<Map<String, Object>>> handleGoogleCallback(@RequestParam("code") String code) {

        //TODO: RX로 비동기 처리 한뒤, 구글 캘린더 API 호출 해야할듯.. -> 완료
        // 먼저 Response 응답 후 캘린더 가져오기
        return Single.fromCallable(() -> {
            // 1. 인증 코드로 액세스 토큰 요청
            String googleTokenUrl = "https://oauth2.googleapis.com/token";
            
            MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
            tokenRequest.add("client_id", googleClientId);
            tokenRequest.add("client_secret", googleClientSecret);
            tokenRequest.add("code", code);
            tokenRequest.add("grant_type", "authorization_code");
            tokenRequest.add("redirect_uri", "https://priorify-one.vercel.app/auth/callback"); // TODO: - 배포시 URL 수정 -> 완료
            
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
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("error", "구글 로그인 실패");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
            }
            
            // ID 토큰에서 사용자 정보 추출
            String googleTokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            Map<String, Object> tokenInfo = restTemplate.getForObject(googleTokenInfoUrl, Map.class);
            
            if (tokenInfo == null || !googleClientId.equals(tokenInfo.get("aud"))) {
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("error", "유효하지 않은 ID 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody);
            }
            
            // 3. 사용자 정보 확인 및 저장
            String googleId = (String) tokenInfo.get("sub");
            String email = (String) tokenInfo.get("email");
            String name = (String) tokenInfo.get("name");
            
            // 4. DB에서 사용자 확인 또는 새로 생성
            User user = userService.findByGoogleId(googleId);
            boolean isNewUser = false;
            
            if (user == null) {
                user = new User();
                user.setGoogleId(googleId);
                user.setEmail(email);
                user.setName(name);
                
                userService.save(user);
                log.info("새 구글 사용자 등록: {}", email);
                isNewUser = true;
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
                    .email(user.getEmail())
                    .highPriorities(user.getHighPriorities())
                    .lowPriorities(user.getLowPriorities())
                    .googleAccessToken(accessToken)
                    .build());
            
            // 8. 리다이렉트 없이 JSON 응답 반환
            return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(responseBody);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.single())
        .doAfterSuccess(response -> {
            // 구글 캘린더 동기화 시작
            Map<String, Object> responseBodyMap = response.getBody();
            if (responseBodyMap != null) {
                Object userObj = responseBodyMap.get("user");
                if (userObj instanceof UserResponseDto) {
                    UserResponseDto userResponseDto = (UserResponseDto) userObj;
                    String userId = userResponseDto.getUserId();
                    String googleAccessToken = userResponseDto.getGoogleAccessToken();
                    
                    // 효용 없는 Rx 제거
                    CalendarSyncResultDto result = googleAPIService.syncGoogleCalendar(userId, googleAccessToken);
                    log.info("캘린더 동기화 완료: {}", result.getMessage());
                }
            }
        })
        .doOnError(error -> {
            log.error("구글 인증 콜백 처리 실패: ", error);
        });
    }
} 