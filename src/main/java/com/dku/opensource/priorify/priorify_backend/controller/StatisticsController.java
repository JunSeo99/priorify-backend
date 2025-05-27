package com.dku.opensource.priorify.priorify_backend.controller;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dku.opensource.priorify.priorify_backend.service.StatisticsService;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }
    
    @GetMapping("/comprehensive")
    public ResponseEntity<Map<String, Object>> getComprehensiveStatistics(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days
        ) {
        String userId = (String) request.getAttribute("userId");
        Map<String, Object> statistics = statisticsService.getComprehensiveStatistics(userId, days);
        return ResponseEntity.ok(statistics);
    }
    
    @GetMapping("/category")
    public ResponseEntity<List<Document>> getUserStatistics(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days
        ) {
        String userId = (String) request.getAttribute("userId");
        List<Document> statistics = statisticsService.getUserStatistics(userId, days);
        return ResponseEntity.ok(statistics);
    }
    
}
