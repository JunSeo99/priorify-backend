package com.dku.opensource.priorify.priorify_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeDto {
    
    private String id;
    private String label;
    private String type;  // "user", "category", "schedule"
    private Integer level; // 0: user, 1: category, 2: schedule
    
    // Schedule 노드의 경우
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private String startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private String endTime;
    
    private Double priority;
    private String status;
    private Double urgencyScore;
    private Double categoryWeight;
    
    // Category 노드의 경우
    private Integer scheduleCount;
    private Double avgPriority;
    
    // 연결된 유사 스케줄들 (schedule 노드만)
    private List<String> similarScheduleIds;
} 