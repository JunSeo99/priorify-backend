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
public class ScheduleNodeDto {
    
    private String id;
    private String title;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private String startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private String endTime;
    
    private Double priority;        // 계산된 중요도
    private String status;          // 일정 상태
    private List<String> categories; // 카테고리들
    
    // Node Graph 연결 정보
    private List<SimilarScheduleDto> similarSchedules; // 유사한 스케줄들
    private Double urgencyScore;    // 긴급도 점수
    private Double categoryWeight;  // 카테고리 가중치
} 