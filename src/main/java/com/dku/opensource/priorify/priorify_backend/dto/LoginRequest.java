package com.dku.opensource.priorify.priorify_backend.dto;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String password;
} 