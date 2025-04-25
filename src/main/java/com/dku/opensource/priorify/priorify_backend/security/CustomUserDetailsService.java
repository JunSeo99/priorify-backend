package com.dku.opensource.priorify.priorify_backend.security;

import com.dku.opensource.priorify.priorify_backend.service.UserService;
import com.dku.opensource.priorify.priorify_backend.model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    // @Override
    // public UserDetails loadUserByUser(String userId) throws UsernameNotFoundException {
    //     User user = userService.findById(new ObjectId(userId))
    //             .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));

    //     return new org.springframework.security.core.userdetails.User(
    //         user.getId().toString(),
    //         user.getPassword(),
    //         new ArrayList<>()
    //     );
    // }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.findByName(username);

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPassword(),
                new ArrayList<>()
            );
    }
} 