package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.dto.*;
import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import com.dku.opensource.priorify.priorify_backend.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {
    
    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    
    // 중요도 계산 상수
    private static final double URGENCY_COEFFICIENT_A = 0.02;
    private static final double URGENCY_COEFFICIENT_B = 0.1;
    private static final double HIGH_PRIORITY_WEIGHT = 1.5;
    private static final double LOW_PRIORITY_WEIGHT = 0.7;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    /**
     * 스케줄 생성
     */
    public Schedule createSchedule(Schedule schedule) {
        schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(schedule);
    }

    /**
     * 스케줄 업데이트
     */
    public Schedule updateSchedule(Schedule schedule) {
        schedule.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(schedule);
    }

    /**
     * 사용자의 모든 스케줄을 Node Connection Graph 형태로 조회
     * 구조: User(Root) -> Categories -> Schedules
     */
    public ScheduleGraphResponseDto getScheduleGraph(String userId) {
        Optional<User> userOpt = userService.findById(new ObjectId(userId));
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다: " + userId);
        }
        User user = userOpt.get();

        // 1. 카테고리별 스케줄 집계 (Document 형식 Aggregation)
        List<Document> categorySchedules = getCategoryScheduleAggregation(userId, user);

        // 2. 그래프 노드와 엣지 생성
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        
        // 3. 루트 사용자 노드 생성
        GraphNodeDto rootUser = createUserNode(user);
        nodes.add(rootUser);
        
        // 4. 카테고리 및 스케줄 노드 생성
        int totalSchedules = 0;
        int totalCategories = categorySchedules.size();
        double totalPriority = 0.0;
        List<ScheduleListDto> allSchedules = new ArrayList<>();
        
        // 스케줄 ID를 키로 하는 Map을 생성하여 중복 방지
        Map<String, GraphNodeDto> scheduleNodes = new HashMap<>();
        Map<String, ScheduleListDto> scheduleList = new HashMap<>();
        
        for (Document categoryDoc : categorySchedules) {
            String categoryName = categoryDoc.getString("_id");
            List<Document> schedules = categoryDoc.getList("schedules", Document.class);
            Double avgPriority = categoryDoc.getDouble("avgPriority");
            
            // 카테고리 노드 생성
            GraphNodeDto categoryNode = createCategoryNode(categoryName, schedules.size(), avgPriority);
            nodes.add(categoryNode);
            
            // User -> Category 엣지 생성
            edges.add(createEdge(rootUser.getId(), categoryNode.getId(), "user-category", avgPriority));
            
            // 스케줄 노드들 생성
            for (Document scheduleDoc : schedules) {
                String scheduleId = scheduleDoc.getString("_id");
                
                // 이미 처리된 스케줄인 경우 엣지만 추가
                if (scheduleNodes.containsKey(scheduleId)) {
                    edges.add(createEdge(categoryNode.getId(), "schedule_" + scheduleId, 
                            "category-schedule", scheduleNodes.get(scheduleId).getPriority()));
                    continue;
                }
                
                // 새로운 스케줄 노드 생성
                GraphNodeDto scheduleNode = createScheduleNode(scheduleDoc, user);
                scheduleNodes.put(scheduleId, scheduleNode);
                nodes.add(scheduleNode);
                
                // 스케줄 목록 DTO 생성 및 추가 (원본 categories 사용)
                List<String> originalCategories = scheduleDoc.getList("originalCategories", String.class);
                System.out.println("originalCategories for schedule " + scheduleId + ": " + originalCategories);
                
                ScheduleListDto scheduleListDto = createScheduleListDto(scheduleDoc, originalCategories);
                scheduleList.put(scheduleId, scheduleListDto);
                
                // Category -> Schedule 엣지 생성
                edges.add(createEdge(categoryNode.getId(), scheduleNode.getId(), 
                        "category-schedule", scheduleNode.getPriority()));
                
                // 유사한 스케줄들과의 연결 엣지 생성
                List<String> similarIds = findSimilarScheduleIds(scheduleId, userId);
                scheduleNode.setSimilarScheduleIds(similarIds);
                
                for (String similarId : similarIds) {
                    edges.add(createEdge(scheduleNode.getId(), similarId, "schedule-schedule", 0.75));
                }
                
                totalPriority += scheduleNode.getPriority();
                totalSchedules++;
            }
        }
        
        // 5. 통계 정보 계산
        Double averagePriority = totalSchedules > 0 ? totalPriority / totalSchedules : 0.0;
        List<String> topCategories = getTopCategoriesFromAggregation(categorySchedules);
        
        // 6. 그래프 메타데이터 생성
        GraphMetadataDto metadata = createGraphMetadata();
        
        return ScheduleGraphResponseDto.builder()
                .nodes(nodes)
                .edges(edges)
                .rootUser(rootUser)
                .schedules(new ArrayList<>(scheduleList.values()))
                .totalSchedules(totalSchedules)
                .totalCategories(totalCategories)
                .averagePriority(averagePriority)
                .topCategories(topCategories)
                .metadata(metadata)
                .build();
    }
    
    /**
     * Document 형식 Aggregation으로 카테고리별 스케줄 집계
     */
    private List<Document> getCategoryScheduleAggregation(String userId, User user) {
        // MongoDB Aggregation Pipeline을 Document 형식으로 구성
        List<Document> pipeline = Arrays.asList(
            // Stage 1: 사용자의 활성 스케줄 필터링
            new Document("$match", new Document()
                .append("userId", new ObjectId(userId))
                .append("status", "active")),
            
            // Stage 2: 원본 categories 정보를 보존하면서 unwind
            new Document("$addFields", new Document()
                .append("originalCategories", "$categories")), // 원본 categories 보존
            
            // Stage 3: categories 배열을 풀어서 각 카테고리별로 문서 생성
            new Document("$unwind", new Document()
                .append("path", "$categories")
                .append("preserveNullAndEmptyArrays", true)),
            
            // Stage 4: 중요도 계산 추가
            new Document("$addFields", new Document()
                .append("urgencyScore", createUrgencyScoreExpression())
                .append("categoryWeight", createCategoryWeightExpression(user))),
            new Document("$addFields", new Document()
                .append("priority", new Document("$multiply", Arrays.asList("$urgencyScore", "$categoryWeight")))),
            
            // Stage 5: 카테고리별 그룹화 (원본 categories 정보 포함)
            new Document("$group", new Document()
                .append("_id", new Document("$ifNull", Arrays.asList("$categories", "기타2")))
                .append("schedules", new Document("$push", new Document()
                    .append("_id", new Document("$toString", "$_id"))
                    .append("title", "$title")
                    .append("startAt", "$startAt")
                    .append("endAt", "$endAt")
                    .append("status", "$status")
                    .append("priority", "$priority")
                    .append("urgencyScore", "$urgencyScore")
                    .append("categoryWeight", "$categoryWeight")
                    .append("originalCategories", "$originalCategories"))) // 원본 categories 포함
                .append("scheduleCount", new Document("$sum", 1))
                .append("avgPriority", new Document("$avg", "$priority"))
                .append("totalPriority", new Document("$sum", "$priority"))),
            
            // Stage 6: 스케줄 수가 많은 카테고리 순으로 정렬
            new Document("$sort", new Document("scheduleCount", -1))
        );
        
        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
                
        return results;
    }
    
    /**
     * 긴급도 계산을 위한 MongoDB Expression 생성
     */
    private Document createUrgencyScoreExpression() {
        return new Document("$let", new Document()
            .append("vars", new Document()
                .append("hoursRemaining", new Document("$divide", Arrays.asList(
                    new Document("$subtract", Arrays.asList("$endAt", "$$NOW")),
                    3600000 // milliseconds to hours
                ))))
            .append("in", new Document("$cond", Arrays.asList(
                new Document("$lte", Arrays.asList("$$hoursRemaining", 0)),
                0.01, // 이미 지난 일정은 최저 우선순위
                new Document("$max", Arrays.asList(
                    0.1, // 최소값
                    new Document("$multiply", Arrays.asList(
                        -1,
                        new Document("$ln", new Document("$add", Arrays.asList(
                            new Document("$multiply", Arrays.asList(URGENCY_COEFFICIENT_A, "$$hoursRemaining")),
                            URGENCY_COEFFICIENT_B
                        )))
                    ))
                ))
            ))));
    }
    
    /**
     * 카테고리 가중치 계산을 위한 MongoDB Expression 생성
     */
    private Document createCategoryWeightExpression(User user) {
        List<String> highPriorities = user.getHighPriorities() != null ? 
                user.getHighPriorities().stream()
                        .map(cp -> cp.getCategory())
                        .collect(Collectors.toList()) : new ArrayList<>();
                        
        List<String> lowPriorities = user.getLowPriorities() != null ?
                user.getLowPriorities().stream()
                        .map(cp -> cp.getCategory())
                        .collect(Collectors.toList()) : new ArrayList<>();
        System.out.println("highPriorities: " + highPriorities);
        System.out.println("lowPriorities: " + lowPriorities);
        return new Document("$cond", Arrays.asList(
            new Document("$in", Arrays.asList("$categories", highPriorities)),
            HIGH_PRIORITY_WEIGHT,
            new Document("$cond", Arrays.asList(
                new Document("$in", Arrays.asList("$categories", lowPriorities)),
                LOW_PRIORITY_WEIGHT,
                1.0
            ))
        ));
    }
    
    /**
     * 사용자 루트 노드 생성
     */
    private GraphNodeDto createUserNode(User user) {
        return GraphNodeDto.builder()
                .id("user_" + user.getId().toString())
                .label(user.getName())
                .type("user")
                .level(0)
                .build();
    }
    
    /**
     * 카테고리 노드 생성
     */
    private GraphNodeDto createCategoryNode(String categoryName, int scheduleCount, Double avgPriority) {
        return GraphNodeDto.builder()
                .id("category_" + categoryName.replaceAll(" ", "_"))
                .label(categoryName)
                .type("category")
                .level(1)
                .scheduleCount(scheduleCount)
                .avgPriority(avgPriority)
                .build();
    }
    
    /**
     * 스케줄 노드 생성
     */
    private GraphNodeDto createScheduleNode(Document scheduleDoc, User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        Date startAt = scheduleDoc.getDate("startAt");
        Date endAt = scheduleDoc.getDate("endAt");
        
        return GraphNodeDto.builder()
                .id("schedule_" + scheduleDoc.getString("_id"))
                .label(scheduleDoc.getString("title"))
                .type("schedule")
                .level(2)
                .startTime(startAt != null ? 
                    LocalDateTime.ofInstant(startAt.toInstant(), java.time.ZoneId.systemDefault()).format(formatter) : "")
                .endTime(endAt != null ? 
                    LocalDateTime.ofInstant(endAt.toInstant(), java.time.ZoneId.systemDefault()).format(formatter) : "")
                .priority(scheduleDoc.getDouble("priority"))
                .status(scheduleDoc.getString("status"))
                .urgencyScore(scheduleDoc.getDouble("urgencyScore"))
                .categoryWeight(scheduleDoc.getDouble("categoryWeight"))
                .build();
    }
    
    /**
     * 엣지 생성
     */
    private GraphEdgeDto createEdge(String source, String target, String type, Double weight) {
        String color = getEdgeColor(type, weight);
        Integer thickness = getEdgeThickness(weight);
        
        return GraphEdgeDto.builder()
                .id(source + "_to_" + target)
                .source(source)
                .target(target)
                .type(type)
                .weight(weight)
                .color(color)
                .thickness(thickness)
                .build();
    }
    
    /**
     * 유사한 스케줄 ID들 찾기 (Document 형식 Vector Search)
     */
    private List<String> findSimilarScheduleIds(String scheduleId, String userId) {
        // 현재 스케줄의 임베딩 벡터 조회
        Schedule currentSchedule = mongoTemplate.findById(scheduleId, Schedule.class);
        if (currentSchedule == null || currentSchedule.getEmbedding() == null || 
            currentSchedule.getEmbedding().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Double> queryVector = currentSchedule.getEmbedding();
        
        // Document 형식 Vector Search Pipeline
        List<Document> pipeline = Arrays.asList(
            // Stage 1: Vector Search
            new Document("$vectorSearch", new Document()
                .append("index", "vector_index")
                .append("path", "embedding")
                .append("queryVector", queryVector)
                .append("numCandidates", 100)
                .append("limit", 20)
                .append("filter", new Document()
                    .append("userId", new ObjectId(userId))
                    .append("status", "active"))),
            
            // Stage 2: 자기 자신 제외
            new Document("$match", new Document()
                .append("_id", new Document("$ne", new ObjectId(scheduleId)))),
            
            // Stage 3: similarity score 추가
            new Document("$addFields", new Document()
                .append("similarity", new Document("$meta", "vectorSearchScore"))),
            
            // Stage 4: 임계값 이상만 필터링
            new Document("$match", new Document()
                .append("similarity", new Document("$gte", SIMILARITY_THRESHOLD))),
            
            // Stage 5: 상위 5개만 선택
            new Document("$limit", 5),
            
            // Stage 6: ID만 프로젝션
            new Document("$project", new Document()
                .append("_id", new Document("$toString", "$_id")))
        );
        
        try {
            List<Document> results = new ArrayList<>();
            mongoTemplate.getCollection("schedules")
                    .aggregate(pipeline, Document.class)
                    .into(results);
            
            return results.stream()
                    .map(doc -> "schedule_" + doc.getString("_id"))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Vector Search 실행 중 오류 발생: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 상위 카테고리들 추출
     */
    private List<String> getTopCategoriesFromAggregation(List<Document> categorySchedules) {
        return categorySchedules.stream()
                .limit(5)
                .map(doc -> doc.getString("_id"))
                .collect(Collectors.toList());
    }
    
    /**
     * 그래프 메타데이터 생성
     */
    private GraphMetadataDto createGraphMetadata() {
        return GraphMetadataDto.builder()
                .layoutType("hierarchical")
                .maxDepth(2)
                .userNodeColor("#4A90E2")
                .categoryNodeColor("#7ED321")
                .scheduleNodeColor("#F5A623")
                .userNodeSize(60)
                .categoryNodeSize(40)
                .scheduleNodeSize(30)
                .highPriorityColor("#D0021B")
                .medPriorityColor("#F5A623")
                .lowPriorityColor("#50E3C2")
                .build();
    }
    
    /**
     * 엣지 색상 결정
     */
    private String getEdgeColor(String type, Double weight) {
        switch (type) {
            case "user-category": return "#4A90E2";
            case "category-schedule": 
                if (weight > 7.0) return "#D0021B";  // 높은 우선순위
                else if (weight > 4.0) return "#F5A623"; // 중간 우선순위
                else return "#50E3C2"; // 낮은 우선순위
            case "schedule-schedule": return "#9013FE"; // 유사도 연결
            default: return "#CCCCCC";
        }
    }
    
    /**
     * 엣지 두께 결정
     */
    private Integer getEdgeThickness(Double weight) {
        System.out.println("weight: " + weight);

        if (weight > 7.0) return 4;
        else if (weight > 4.0) return 3;
        else return 2;
    }
    
    /**
     * 스케줄 목록 DTO 생성
     */
    private ScheduleListDto createScheduleListDto(Document scheduleDoc, List<String> categories) {
        Date startAt = scheduleDoc.getDate("startAt");
        Date endAt = scheduleDoc.getDate("endAt");
        
        // 카테고리가 null이거나 비어있으면 "기타" 카테고리 추가
        List<String> finalCategories = (categories != null && !categories.isEmpty()) ?
                new ArrayList<>(categories) : new ArrayList<>(List.of("기타2"));
        
        return ScheduleListDto.builder()
                .id(scheduleDoc.getString("_id"))
                .title(scheduleDoc.getString("title"))
                .startDate(startAt != null ? 
                    LocalDateTime.ofInstant(startAt.toInstant(), java.time.ZoneId.systemDefault()) : null)
                .endDate(endAt != null ? 
                    LocalDateTime.ofInstant(endAt.toInstant(), java.time.ZoneId.systemDefault()) : null)
                .categories(new ArrayList<>(finalCategories))
                .priority(scheduleDoc.getDouble("priority"))
                .status(scheduleDoc.getString("status"))
                .build();
    }
    
    /**
     * 특정 스케줄 조회
     */
    public Schedule getScheduleById(String scheduleId) {
        return mongoTemplate.findById(scheduleId, Schedule.class);
    }
    
    /**
     * 사용자의 모든 스케줄 조회
     */
    public List<ScheduleListDto> getUserSchedules(String userId) {
        List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("userId", new ObjectId(userId))
                .append("status", "active")),
            new Document("$sort", new Document("startAt", 1)),
            new Document("$project", new Document()
                .append("_id", new Document("$toString", "$_id"))
                .append("title", "$title")
                .append("startAt", "$startAt")
                .append("endAt", "$endAt")
                .append("categories", "$categories")
                .append("status", "$status")
                .append("priority", "$priority"))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
                
        return results.stream()
                .map(doc -> {
                    List<String> categories = doc.getList("categories", String.class);
                    System.out.println("categories2: " + categories);
                    return createScheduleListDto(doc, categories);
                })
                .collect(Collectors.toList());
    }
} 