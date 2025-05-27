package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.AnalyticsService;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategoryAnalytics(
                HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String userId = (String) request.getAttribute("userId");
        // Map<String, Object> analytics = analyticsService.getCategoryAnalytics(new ObjectId(userId), start, end);
        // return ResponseEntity.ok(analytics);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/productivity")
    public ResponseEntity<Map<String, Object>> getProductivityMetrics(
        HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String userId = (String) request.getAttribute("userId");
        // Map<String, Object> metrics = analyticsService.getProductivityMetrics(new ObjectId(userId), start, end);
        return ResponseEntity.ok(null);
    }
} 