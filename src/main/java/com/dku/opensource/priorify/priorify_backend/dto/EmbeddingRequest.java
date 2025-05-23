package com.dku.opensource.priorify.priorify_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {
    
    @JsonProperty("texts")
    @NotEmpty
    private List<String> texts;
} 