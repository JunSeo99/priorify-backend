package java.com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.UserService;
import com.dku.opensource.priorify.priorify_backend.dto.LoginRequest;
import com.dku.opensource.priorify.priorify_backend.dto.SignUpRequest;
import com.dku.opensource.priorify.priorify_backend.dto.UserResponseDto;
import com.dku.opensource.priorify.priorify_backend.model.User;
import com.dku.opensource.priorify.priorify_backend.security.JwtTokenProvider;
import com.dku.opensource.priorify.priorify_backend.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        if(userService.findByName(signUpRequest.getName()) != null) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Email is already in use!");
        }

        if(!signUpRequest.getPassword().equals(signUpRequest.getPasswordConfirm())) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Password confirmation does not match!");
        }

        // Creating user's account
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));

        User savedUser = userService.save(user);

        // Generate JWT token
        String token = tokenProvider.generateToken(user.getName().toString());

        UserResponseDto response = UserResponseDto.builder()
                .userId(savedUser.getId().toHexString())
                .name(savedUser.getName())
                .message("User registered successfully")
                .build();

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + token)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getName(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // 사용자 정보 가져오기
        User user = userService.findByName(loginRequest.getName());
        String jwt = tokenProvider.generateToken(authentication);

        UserResponseDto response = UserResponseDto.builder()
                .userId(user.getId().toHexString())
                .name(user.getName())
                .message("Login successful")
                .build();

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + jwt)
                .body(response);
    }
} 