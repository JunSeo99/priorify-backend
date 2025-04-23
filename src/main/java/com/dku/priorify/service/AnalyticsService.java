package com.dku.priorify.service;

import com.dku.priorify.model.Schedule;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final ScheduleService scheduleService;

    public AnalyticsService(MongoTemplate mongoTemplate, ScheduleService scheduleService) {
        this.mongoTemplate = mongoTemplate;
        this.scheduleService = scheduleService;
    }

    public Map<String, Object> getCategoryAnalytics(ObjectId userId, LocalDateTime start, LocalDateTime end) {
        List<Schedule> schedules = scheduleService.findSchedulesByDateRange(userId, start, end);
        
        Map<String, Object> analytics = new HashMap<>();
        
        // 카테고리별 일정 수
        Map<String, Long> categoryCount = schedules.stream()
                .collect(Collectors.groupingBy(Schedule::getCategory, Collectors.counting()));
        analytics.put("categoryCount", categoryCount);
        
        // 카테고리별 완료율
        Map<String, Double> completionRate = schedules.stream()
                .collect(Collectors.groupingBy(Schedule::getCategory,
                        Collectors.collectingAndThen(
                                Collectors.partitioningBy(Schedule::isCompleted, Collectors.counting()),
                                map -> (double) map.get(true) / (map.get(true) + map.get(false))
                        )));
        analytics.put("completionRate", completionRate);
        
        // 카테고리별 평균 우선순위 점수
        Map<String, Double> avgPriorityScore = schedules.stream()
                .collect(Collectors.groupingBy(Schedule::getCategory,
                        Collectors.averagingDouble(Schedule::getPriorityScore)));
        analytics.put("avgPriorityScore", avgPriorityScore);

        // 시간대별 일정 분포
        Map<Integer, Long> hourlyDistribution = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getDatetime().getHour(), Collectors.counting()));
        analytics.put("hourlyDistribution", hourlyDistribution);

        return analytics;
    }

    public Map<String, Object> getProductivityMetrics(ObjectId userId, LocalDateTime start, LocalDateTime end) {
        List<Schedule> schedules = scheduleService.findSchedulesByDateRange(userId, start, end);
        
        Map<String, Object> metrics = new HashMap<>();
        
        // 전체 완료율
        long totalSchedules = schedules.size();
        long completedSchedules = schedules.stream().filter(Schedule::isCompleted).count();
        double overallCompletionRate = totalSchedules > 0 ? (double) completedSchedules / totalSchedules : 0;
        metrics.put("overallCompletionRate", overallCompletionRate);
        
        // 우선순위별 완료율
        Map<String, Double> priorityCompletionRate = new HashMap<>();
        priorityCompletionRate.put("high", calculateCompletionRateByPriority(schedules, 0.7, 1.0));
        priorityCompletionRate.put("medium", calculateCompletionRateByPriority(schedules, 0.4, 0.7));
        priorityCompletionRate.put("low", calculateCompletionRateByPriority(schedules, 0.0, 0.4));
        metrics.put("priorityCompletionRate", priorityCompletionRate);
        
        // 생산성 점수 (완료율과 우선순위 점수의 가중 평균)
        double productivityScore = schedules.stream()
                .mapToDouble(s -> s.isCompleted() ? s.getPriorityScore() : 0)
                .average()
                .orElse(0.0);
        metrics.put("productivityScore", productivityScore);

        return metrics;
    }

    private double calculateCompletionRateByPriority(List<Schedule> schedules, double minScore, double maxScore) {
        List<Schedule> prioritySchedules = schedules.stream()
                .filter(s -> s.getPriorityScore() >= minScore && s.getPriorityScore() < maxScore)
                .collect(Collectors.toList());
        
        if (prioritySchedules.isEmpty()) {
            return 0.0;
        }
        
        long completed = prioritySchedules.stream().filter(Schedule::isCompleted).count();
        return (double) completed / prioritySchedules.size();
    }
} 