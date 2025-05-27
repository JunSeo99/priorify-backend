package com.dku.opensource.priorify.priorify_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import com.dku.opensource.priorify.priorify_backend.model.User;

@Service
public class StatisticsService {

    private final ScheduleService scheduleService;
    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    
    // ScheduleService와 동일한 상수 사용
    // private static final double URGENCY_COEFFICIENT_A = 0.02;
    // private static final double URGENCY_COEFFICIENT_B = 0.1;
    private static final double HIGH_PRIORITY_WEIGHT = 2.5;
    private static final double LOW_PRIORITY_WEIGHT = 0.5;

    public StatisticsService(ScheduleService scheduleService, MongoTemplate mongoTemplate, UserService userService) {
        this.scheduleService = scheduleService;
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
    }

    /**
     * 통합 통계 데이터 반환 - 통계 페이지에 필요한 모든 데이터
     */
    public Map<String, Object> getComprehensiveStatistics(String userId, int days) {
        User user = userService.findById(new ObjectId(userId))
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
        
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);
        
        Map<String, Object> result = new HashMap<>();
        
        List<Document> categoryStats = getCategoryStatisticsWithPriority(userId, user, startDate, endDate);
        result.put("categoryStats", categoryStats);
        Map<String, Object> summary = getOverallSummary(categoryStats);
        result.put("summary", summary);
        Map<String, Object> priorityDistribution = getPriorityDistribution(categoryStats);
        result.put("priorityDistribution", priorityDistribution);
        List<Document> timeBasedPriority = getTimeBasedPriorityDistribution(userId, user, startDate, endDate);
        result.put("timeBasedPriority", timeBasedPriority);
        Map<String, Object> completionStats = getCompletionStatistics(userId, startDate, endDate);
        result.put("completionStats", completionStats);
        Map<String, Object> prioritySettings = getUserPrioritySettings(user);
        result.put("prioritySettings", prioritySettings);
        
        return result;
    }

    // 카테고리별
    private List<Document> getCategoryStatisticsWithPriority(String userId, User user, 
                                                           LocalDateTime startDate, LocalDateTime endDate) {
        List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("$and", Arrays.asList(
                    new Document("userId", new ObjectId(userId)),
                    new Document("startAt", new Document()
                        .append("$gte", startDate)),
                    new Document("startAt", new Document()
                        .append("$lte", endDate))
                ))
            ),
            new Document("$unwind", new Document()
                .append("path", "$categories")
                .append("preserveNullAndEmptyArrays", true)),
            new Document("$addFields", new Document()
                .append("urgencyScore", createSimplePriorityExpression())
                .append("categoryWeight", createCategoryWeightExpression(user))),
            new Document("$addFields", new Document()
                .append("priority", new Document("$multiply", Arrays.asList("$urgencyScore", "$categoryWeight")))),
            new Document("$group", new Document()
                .append("_id", new Document("$ifNull", Arrays.asList("$categories", "기타")))
                .append("totalSchedules", new Document("$sum", 1))
                .append("totalPriority", new Document("$sum", "$priority"))
                .append("avgPriority", new Document("$avg", "$priority"))
                .append("maxPriority", new Document("$max", "$priority"))
                .append("minPriority", new Document("$min", "$priority"))
                .append("avgUrgency", new Document("$avg", "$urgencyScore"))
                .append("avgCategoryWeight", new Document("$avg", "$categoryWeight"))
                .append("completedCount", new Document("$sum", 
                    new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$status", "completed")),
                        1, 0))))
                .append("totalDuration", new Document("$sum", 
                    new Document("$divide", Arrays.asList(
                        new Document("$subtract", Arrays.asList("$endAt", "$startAt")),
                        3600000)))) // 시간 단위
                .append("scheduleDetails", new Document("$push", new Document()
                    .append("title", "$title")
                    .append("priority", "$priority")
                    .append("urgency", "$urgencyScore")
                    .append("categoryWeight", "$categoryWeight")
                    .append("status", "$status")
                    .append("startAt", "$startAt")))),
            new Document("$addFields", new Document()
                .append("completionRate", new Document("$cond", Arrays.asList(
                    new Document("$eq", Arrays.asList("$totalSchedules", 0)),
                    0,
                    new Document("$divide", Arrays.asList("$completedCount", "$totalSchedules")))))
                .append("avgDuration", new Document("$cond", Arrays.asList(
                    new Document("$eq", Arrays.asList("$totalSchedules", 0)),
                    0,
                    new Document("$divide", Arrays.asList("$totalDuration", "$totalSchedules")))))),
            new Document("$sort", new Document("totalPriority", -1))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
        
        System.out.println("getCategoryStatisticsWithPriority results: " + results.toString());
        return results;
    }

    // 전체 통계 요약
    private Map<String, Object> getOverallSummary(List<Document> categoryStats) {
        Map<String, Object> summary = new HashMap<>();
        
        int totalSchedules = categoryStats.stream()
                .mapToInt(doc -> doc.getInteger("totalSchedules", 0))
                .sum();
        
        int totalCompleted = categoryStats.stream()
                .mapToInt(doc -> doc.getInteger("completedCount", 0))
                .sum();
        
        double totalPriority = categoryStats.stream()
                .mapToDouble(doc -> doc.getDouble("totalPriority"))
                .sum();
        
        double totalDuration = categoryStats.stream()
                .mapToDouble(doc -> doc.getDouble("totalDuration"))
                .sum();
        
        summary.put("totalSchedules", totalSchedules);
        summary.put("totalCompleted", totalCompleted);
        summary.put("overallCompletionRate", totalSchedules > 0 ? (double) totalCompleted / totalSchedules : 0.0);
        summary.put("totalPriority", totalPriority);
        summary.put("avgPriority", totalSchedules > 0 ? totalPriority / totalSchedules : 0.0);
        summary.put("totalHours", totalDuration);
        summary.put("avgHoursPerSchedule", totalSchedules > 0 ? totalDuration / totalSchedules : 0.0);
        summary.put("totalCategories", categoryStats.size());
        
        return summary;
    }

    // 우선순위별 분포 계산
    private Map<String, Object> getPriorityDistribution(List<Document> categoryStats) {
        Map<String, Object> distribution = new HashMap<>();
        
        List<Double> allPriorities = new ArrayList<>();
        for (Document categoryDoc : categoryStats) {
            List<Document> schedules = categoryDoc.getList("scheduleDetails", Document.class);
            for (Document schedule : schedules) {
                allPriorities.add(schedule.getDouble("priority"));
            }
        }
        
        if (allPriorities.isEmpty()) {
            distribution.put("high", 0);
            distribution.put("medium", 0);
            distribution.put("low", 0);
            return distribution;
        }
        
        // 우선순위를 3단계로 분류
        allPriorities.sort((a, b) -> Double.compare(b, a)); // 내림차순 정렬
        int total = allPriorities.size();
        int highCount = (int) allPriorities.stream().filter(p -> p >= 3.0).count();
        int lowCount = (int) allPriorities.stream().filter(p -> p <= 1.0).count();
        int mediumCount = total - highCount - lowCount;
        
        distribution.put("high", highCount);
        distribution.put("medium", mediumCount);
        distribution.put("low", lowCount);
        distribution.put("highPercentage", total > 0 ? (double) highCount / total * 100 : 0.0);
        distribution.put("mediumPercentage", total > 0 ? (double) mediumCount / total * 100 : 0.0);
        distribution.put("lowPercentage", total > 0 ? (double) lowCount / total * 100 : 0.0);
        
        return distribution;
    }

    // 시간대별 중요도 분포
    private List<Document> getTimeBasedPriorityDistribution(String userId, User user, 
                                                          LocalDateTime startDate, LocalDateTime endDate) {
        List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("userId", new ObjectId(userId))
                .append("startAt", new Document()
                    .append("$gte", startDate)
                    .append("$lte", endDate))),
            
            new Document("$unwind", new Document()
                .append("path", "$categories")
                .append("preserveNullAndEmptyArrays", true)),
            
            new Document("$addFields", new Document()
                .append("hour", new Document("$hour", "$startAt"))
                .append("dayOfWeek", new Document("$dayOfWeek", "$startAt"))
                .append("urgencyScore", createUrgencyScoreExpression())
                .append("categoryWeight", createCategoryWeightExpression(user))),
            
            new Document("$addFields", new Document()
                .append("priority", new Document("$multiply", Arrays.asList("$urgencyScore", "$categoryWeight")))),
            
            new Document("$group", new Document()
                .append("_id", new Document()
                    .append("hour", "$hour")
                    .append("dayOfWeek", "$dayOfWeek"))
                .append("scheduleCount", new Document("$sum", 1))
                .append("avgPriority", new Document("$avg", "$priority"))
                .append("totalPriority", new Document("$sum", "$priority"))),
            
            new Document("$sort", new Document("_id.dayOfWeek", 1).append("_id.hour", 1))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
                
        return results;
    }

    // 완료율 통계
    private Map<String, Object> getCompletionStatistics(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("userId", new ObjectId(userId))
                .append("startAt", new Document()
                    .append("$gte", startDate)
                    .append("$lte", endDate))),
            
            new Document("$group", new Document()
                .append("_id", "$status")
                .append("count", new Document("$sum", 1))),
            
            new Document("$group", new Document()
                .append("_id", null)
                .append("statusCounts", new Document("$push", new Document()
                    .append("status", "$_id")
                    .append("count", "$count")))
                .append("totalSchedules", new Document("$sum", "$count")))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);

        if (results.isEmpty()) {
            return Map.of("total", 0, "completed", 0, "active", 0, "completionRate", 0.0);
        }

        Document result = results.get(0);
        List<Document> statusCounts = result.getList("statusCounts", Document.class);
        int total = result.getInteger("totalSchedules", 0);
        
        Map<String, Integer> counts = new HashMap<>();
        counts.put("completed", 0);
        counts.put("active", 0);
        
        for (Document statusDoc : statusCounts) {
            String status = statusDoc.getString("status");
            Integer count = statusDoc.getInteger("count");
            counts.put(status, count);
        }
        
        Map<String, Object> completionStats = new HashMap<>();
        completionStats.put("total", total);
        completionStats.put("completed", counts.get("completed"));
        completionStats.put("active", counts.get("active"));
        completionStats.put("completionRate", total > 0 ? (double) counts.get("completed") / total : 0.0);
        
        return completionStats;
    }

    // 사용자 우선순위 설정 정보
    private Map<String, Object> getUserPrioritySettings(User user) {
        Map<String, Object> settings = new HashMap<>();
        
        List<Map<String, Object>> highPriorities = user.getHighPriorities().stream()
                .map(cp -> {
                    Map<String, Object> priorityMap = new HashMap<>();
                    priorityMap.put("category", cp.getCategory());
                    priorityMap.put("rank", cp.getRank());
                    priorityMap.put("weight", HIGH_PRIORITY_WEIGHT + (4 - cp.getRank()) * 0.5);
                    return priorityMap;
                })
                .collect(Collectors.toList());
        
        List<Map<String, Object>> lowPriorities = user.getLowPriorities().stream()
                .map(cp -> {
                    Map<String, Object> priorityMap = new HashMap<>();
                    priorityMap.put("category", cp.getCategory());
                    priorityMap.put("rank", cp.getRank());
                    priorityMap.put("weight", LOW_PRIORITY_WEIGHT - (cp.getRank() - 1) * 0.1);
                    return priorityMap;
                })
                .collect(Collectors.toList());
        
        settings.put("highPriorities", highPriorities);
        settings.put("lowPriorities", lowPriorities);
        settings.put("defaultWeight", 1.0);
        
        return settings;
    }

    // 시간 기반 우선순위 계산
    private Document createUrgencyScoreExpression() {
        return new Document("$let", new Document()
            .append("vars", new Document()
                .append("hoursRemaining", new Document("$divide", Arrays.asList(
                    new Document("$subtract", Arrays.asList("$endAt", "$$NOW")),
                    3600000)))) // 밀리초를 시간으로 변환
            .append("in", new Document("$switch", new Document()
                .append("branches", Arrays.asList(
                    // 과거 일정 (이미 지남): 낮은 점수
                    new Document()
                        .append("case", new Document("$lt", Arrays.asList("$$hoursRemaining", 0)))
                        .append("then", 0.5),
                    
                    // 임박한 일정 (24시간 이내): 높은 점수
                    new Document()
                        .append("case", new Document("$lte", Arrays.asList("$$hoursRemaining", 24)))
                        .append("then", 3.0),
                    
                    // 가까운 일정 (1주일 이내): 중상 점수
                    new Document()
                        .append("case", new Document("$lte", Arrays.asList("$$hoursRemaining", 168))) // 7일 = 168시간
                        .append("then", 2.0),
                    
                    // 보통 일정 (1달 이내): 중간 점수
                    new Document()
                        .append("case", new Document("$lte", Arrays.asList("$$hoursRemaining", 720))) // 30일 = 720시간
                        .append("then", 1.5),
                    
                    // 먼 미래 일정: 기본 점수
                    new Document()
                        .append("case", new Document("$gt", Arrays.asList("$$hoursRemaining", 720)))
                        .append("then", 1.0)
                ))
                .append("default", 1.0) // 기본값
            )));
    }

    // 가중치 계산 함수
    private Document createCategoryWeightExpression(User user) {
        Map<String, Integer> highPriorityRanks = user.getHighPriorities() != null ?
                user.getHighPriorities().stream()
                        .collect(Collectors.toMap(
                            cp -> cp.getCategory(),
                            cp -> cp.getRank(),
                            (existing, replacement) -> existing)) : new HashMap<>();
                            
        Map<String, Integer> lowPriorityRanks = user.getLowPriorities() != null ?
                user.getLowPriorities().stream()
                        .collect(Collectors.toMap(
                            cp -> cp.getCategory(),
                            cp -> cp.getRank(),
                            (existing, replacement) -> existing)) : new HashMap<>();
        
        List<Document> switchBranches = new ArrayList<>();
        
        // High Priority 카테고리들
        for (Map.Entry<String, Integer> entry : highPriorityRanks.entrySet()) {
            String category = entry.getKey();
            Integer rank = entry.getValue();
            double weight = HIGH_PRIORITY_WEIGHT + (4 - rank) * 0.5;
            
            switchBranches.add(new Document()
                .append("case", new Document("$eq", Arrays.asList("$categories", category)))
                .append("then", weight));
        }
        
        // Low Priority 카테고리들
        for (Map.Entry<String, Integer> entry : lowPriorityRanks.entrySet()) {
            String category = entry.getKey();
            Integer rank = entry.getValue();
            double weight = LOW_PRIORITY_WEIGHT - (rank - 1) * 0.1;
            
            switchBranches.add(new Document()
                .append("case", new Document("$eq", Arrays.asList("$categories", category)))
                .append("then", weight));
        }
        
        return new Document("$switch", new Document()
            .append("branches", switchBranches)
            .append("default", 1.0));
    }


    private Document createSimplePriorityExpression() {
        return new Document("$let", new Document()
            .append("vars", new Document()
                .append("hoursRemaining", new Document("$divide", Arrays.asList(
                    new Document("$subtract", Arrays.asList("$endAt", "$$NOW")),
                    3600000))))
            .append("in", new Document("$cond", Arrays.asList(
                new Document("$lt", Arrays.asList("$$hoursRemaining", 0)),
                0.8,
                1.0
            ))));
    }


    // 기존 메서드 유지
    public List<Document> getUserStatistics(String userId, int days) {
        List<Document> pipeline = List.of(
            new Document("$match", new Document()
                .append("userId", new ObjectId(userId))),
            new Document("$unwind", "$categories"),
            new Document("$group", new Document()
                .append("_id", "$categories")
                .append("count", new Document("$sum", 1))
                .append("userId", new Document("$first", "$userId"))),
            new Document("$sort", new Document("count", -1))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
        
        return results;
    }
}
