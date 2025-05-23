package com.dku.opensource.priorify.priorify_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.api.services.calendar.Calendar.Acl.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Setter;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Data
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleListDto {
    
    private String id;
    private String title;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endDate;
    
    private ArrayList<String> categories;
    private Double priority;
    private String status;
} 