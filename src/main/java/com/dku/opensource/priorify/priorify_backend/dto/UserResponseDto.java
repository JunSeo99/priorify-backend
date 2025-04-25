package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {
    private String userId;
    private String name;
    private String message;
} 