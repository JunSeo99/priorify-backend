package com.dku.opensource.priorify.priorify_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCalendarEventDto {
    
    private String id;
    private String title;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;
    
    // 임베딩 관련 필드 (처리 후 추가됨)
    private List<Double> embedding;
    private List<String> categories;
    private String originalText;
    
    // 구글 원본 데이터 보존용
    private String googleEventId;
} 