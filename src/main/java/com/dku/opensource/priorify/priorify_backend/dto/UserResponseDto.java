package com.dku.opensource.priorify.priorify_backend.dto;

import java.util.List;

import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {
    private String userId;
    private String name;
    private String email;
    private String googleAccessToken;
    private List<CategoryPriority> highPriorities;
    private List<CategoryPriority> lowPriorities;
} 