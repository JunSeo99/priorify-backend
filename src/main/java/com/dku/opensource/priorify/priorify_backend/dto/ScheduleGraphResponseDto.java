package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGraphResponseDto {
    
    // 그래프 구조
    private List<GraphNodeDto> nodes;    // 모든 노드들 (user, category, schedule)
    private List<GraphEdgeDto> edges;    // 모든 연결 관계
    
    // 루트 사용자 정보
    private GraphNodeDto rootUser;       // 사용자 루트 노드
    
    // 스케줄 목록 (별도 제공)
    private List<ScheduleListDto> schedules; // 사용자의 모든 스케줄 목록
    
    // 통계 정보
    private int totalSchedules;          // 총 스케줄 개수
    private int totalCategories;         // 총 카테고리 개수
    private Double averagePriority;      // 평균 중요도
    private List<String> topCategories;  // 주요 카테고리들
    
    // 그래프 메타데이터
    private GraphMetadataDto metadata;   // 그래프 렌더링 정보
} 