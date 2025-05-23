package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphMetadataDto {
    
    // 그래프 레이아웃 정보
    private String layoutType;       // "hierarchical", "force", "circle"
    private Integer maxDepth;        // 최대 깊이 (보통 2: user -> category -> schedule)
    
    // 색상 스키마
    private String userNodeColor;     // 사용자 노드 색상
    private String categoryNodeColor; // 카테고리 노드 색상
    private String scheduleNodeColor; // 스케줄 노드 색상
    
    // 크기 정보
    private Integer userNodeSize;     // 사용자 노드 크기
    private Integer categoryNodeSize; // 카테고리 노드 크기
    private Integer scheduleNodeSize; // 스케줄 노드 크기
    
    // 우선순위 색상 매핑
    private String highPriorityColor; // 높은 우선순위 색상
    private String medPriorityColor;  // 중간 우선순위 색상
    private String lowPriorityColor;  // 낮은 우선순위 색상
} 