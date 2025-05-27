package com.dku.opensource.priorify.priorify_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResult {
    
    @JsonProperty("original_text")
    private String originalText;
    
    @JsonProperty("embedding")
    private List<Double> embedding;
    
    @JsonProperty("categories")
    private List<String> categories;
} 