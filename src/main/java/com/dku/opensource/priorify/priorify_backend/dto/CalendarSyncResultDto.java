package com.dku.opensource.priorify.priorify_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSyncResultDto {
    
    private String userId;
    private int totalEventsProcessed;
    private int successfulEmbeddings;
    private int failedEmbeddings;
    private LocalDateTime syncStartTime;
    private LocalDateTime syncEndTime;
    private String status; // SUCCESS, PARTIAL_SUCCESS, FAILED
    private String message;
    
    // 편의 메서드들
    public long getSyncDurationMillis() {
        if (syncStartTime != null && syncEndTime != null) {
            return java.time.Duration.between(syncStartTime, syncEndTime).toMillis();
        }
        return 0;
    }
    
    public double getSuccessRate() {
        if (totalEventsProcessed == 0) return 0.0;
        return (double) successfulEmbeddings / totalEventsProcessed * 100.0;
    }
} 