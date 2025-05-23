package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarScheduleDto {
    
    private String id;
    private String title;
    private Double similarity;  // 코사인 유사도 점수 (0.5 이상)
    private String status;
} 