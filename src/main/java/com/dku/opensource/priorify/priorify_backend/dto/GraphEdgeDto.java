package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDto {
    
    private String id;
    private String source;  // 연결의 시작점 노드 ID
    private String target;  // 연결의 끝점 노드 ID
    private String type;    // "user-category", "category-schedule", "schedule-schedule"
    
    // 엣지 가중치/속성
    private Double weight;     // 연결 강도 (유사도, 중요도 등)
    private String label;      // 엣지 라벨 (필요한 경우)
    
    // 시각적 속성 (프론트엔드용)
    private String color;      // 엣지 색상
    private Integer thickness; // 엣지 두께
} 