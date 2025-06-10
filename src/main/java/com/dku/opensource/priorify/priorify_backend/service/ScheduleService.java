package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.dto.*;
import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import com.dku.opensource.priorify.priorify_backend.model.User; 
import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {
    
    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    private final EmailService emailService;
    
    // 중요도 계산 상수
    private static final double URGENCY_COEFFICIENT_A = 0.01;
    private static final double URGENCY_COEFFICIENT_B = 0.1;
    private static final double HIGH_PRIORITY_WEIGHT = 2.5;
    private static final double LOW_PRIORITY_WEIGHT = 0.5;
    private static final double SIMILARITY_THRESHOLD = 0.6;
    
    // 배치 처리 상수
    private static final int BATCH_SIZE = 5; // 한 번에 처리할 사용자 수
    private static final long BATCH_DELAY_MS = 1000; // 배치 간 지연 시간 (밀리초)

    // 스케줄 생성
    public Schedule createSchedule(Schedule schedule) {
        schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(schedule);
    }

    // 스케줄 업데이트
    public Schedule updateSchedule(Schedule schedule) {
        schedule.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(schedule);
    }

    // 사용자의 모든 스케줄을 Node Connection Graph 형태로 조회
    public ScheduleGraphResponseDto getScheduleGraph(String userId, int days) {
        Optional<User> userOpt = userService.findById(new ObjectId(userId));
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다: " + userId);
        }
        User user = userOpt.get();

        // 1. 카테고리별 스케줄 집계 (Document 형식 Aggregation)
        List<Document> categorySchedules = getCategoryScheduleAggregation(userId, user, days);

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
        // List<ScheduleListDto> allSchedules = new ArrayList<>();
        
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
    private List<Document> getCategoryScheduleAggregation(String userId, User user, int days) {
        // MongoDB Aggregation Pipeline을 Document 형식으로 구성
        List<Document> pipeline = Arrays.asList(
            // Stage 1: 사용자의 활성 스케줄 필터링
            new Document("$match", new Document()
                .append("$and", Arrays.asList(
                    new Document("startAt", new Document("$gte", LocalDateTime.now())),
                    new Document("startAt", new Document("$lte", LocalDateTime.now().plusDays(days))),
                    new Document("userId", new ObjectId(userId)),
                    new Document("$or", Arrays.asList(
                        new Document("status", "active"),
                        new Document("status", "completed")
                    ))
                ))),
            new Document("$addFields", new Document()
                .append("originalCategories", "$categories")),
            new Document("$unwind", new Document()
                .append("path", "$categories")
                .append("preserveNullAndEmptyArrays", true)),
            new Document("$addFields", new Document()
                .append("urgencyScore", createUrgencyScoreExpression())
                .append("categoryWeight", createCategoryWeightExpression(user))),
            new Document("$addFields", new Document()
                .append("priority", new Document("$multiply", Arrays.asList("$urgencyScore", "$categoryWeight")))),
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
    
    // 가중치 계산 함수
    private Document createCategoryWeightExpression(User user) {
        List<String> highPriorities = user.getHighPriorities() != null ? 
                user.getHighPriorities().stream()
                        .map(cp -> cp.getCategory())
                        .collect(Collectors.toList()) : new ArrayList<>();
                        
        List<String> lowPriorities = user.getLowPriorities() != null ?
                user.getLowPriorities().stream()
                        .map(cp -> cp.getCategory())
                        .collect(Collectors.toList()) : new ArrayList<>();
        
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
        
        System.out.println("highPriorities: " + highPriorities);
        System.out.println("lowPriorities: " + lowPriorities);
        System.out.println("highPriorityRanks: " + highPriorityRanks);
        System.out.println("lowPriorityRanks: " + lowPriorityRanks);
        
        List<Document> switchBranches = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : highPriorityRanks.entrySet()) {
            String category = entry.getKey();
            Integer rank = entry.getValue();
            double weight = HIGH_PRIORITY_WEIGHT + (4 - rank) * 0.5; // 1순위 4.0, 2순위 3.5, 3순위 3.0
            
            switchBranches.add(new Document()
                .append("case", new Document("$eq", Arrays.asList("$categories", category)))
                .append("then", weight));
        }
        
        for (Map.Entry<String, Integer> entry : lowPriorityRanks.entrySet()) {
            String category = entry.getKey();
            Integer rank = entry.getValue();
            double weight = LOW_PRIORITY_WEIGHT - (rank - 1) * 0.1; // 1순위 0.3, 2순위 0.4, 3순위 0.5
            
            switchBranches.add(new Document()
                .append("case", new Document("$eq", Arrays.asList("$categories", category)))
                .append("then", weight));
        }
        
        return new Document("$switch", new Document()
            .append("branches", switchBranches)
            .append("default", 1.0)); // 기본 가중치
    }
    
    // 사용자 루트 노드 생성
    private GraphNodeDto createUserNode(User user) {
        return GraphNodeDto.builder()
                .id("user_" + user.getId().toString())
                .label(user.getName())
                .type("user")
                .level(0)
                .build();
    }
    
    // 카테고리 노드 생성
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
    
    // 스케줄 노드 생성
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
    
    // 엣지 생성
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
    
    // 유사한 스케줄 ID들 찾기
    private List<String> findSimilarScheduleIds(String scheduleId, String userId) {
        // 현재 스케줄의 임베딩 벡터 조회
        Schedule currentSchedule = mongoTemplate.findById(scheduleId, Schedule.class);
        if (currentSchedule == null || currentSchedule.getEmbedding() == null || 
            currentSchedule.getEmbedding().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Double> queryVector = currentSchedule.getEmbedding();
        
        List<Document> pipeline = Arrays.asList(
            new Document("$vectorSearch", new Document()
                .append("index", "vector_index")
                .append("path", "embedding")
                .append("queryVector", queryVector)
                .append("numCandidates", 100)
                .append("limit", 20)
                .append("filter", new Document()
                    .append("userId", new ObjectId(userId))
                    .append("status", "active"))),
            new Document("$match", new Document()
                .append("_id", new Document("$ne", new ObjectId(scheduleId)))),
            new Document("$addFields", new Document()
                .append("similarity", new Document("$meta", "vectorSearchScore"))),
            new Document("$match", new Document()
                .append("similarity", new Document("$gte", SIMILARITY_THRESHOLD))),
            new Document("$limit", 5),
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
    
    // 상위 카테고리들 추출
    private List<String> getTopCategoriesFromAggregation(List<Document> categorySchedules) {
        return categorySchedules.stream()
                .limit(5)
                .map(doc -> doc.getString("_id"))
                .collect(Collectors.toList());
    }
    
    // 그래프 메타데이터 생성
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
    
    // 엣지 두께 결정
    private Integer getEdgeThickness(Double weight) {
        System.out.println("weight: " + weight);

        if (weight > 7.0) return 4;
        else if (weight > 4.0) return 3;
        else return 2;
    }
    
    // 스케줄 목록 DTO 생성
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
    
    // 특정 스케줄 조회
    public Schedule getScheduleById(String scheduleId) {
        return mongoTemplate.findById(scheduleId, Schedule.class);
    }
    
    // 사용자의 모든 스케줄 조회
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

    public void toggleScheduleStatus(String userId, String scheduleId) {
        Schedule schedule = mongoTemplate.findById(scheduleId, Schedule.class);
        if (schedule == null) {
            throw new RuntimeException("스케줄을 찾을 수 없습니다.");
        }
        schedule.setStatus(schedule.getStatus().equals("active") ? "completed" : "active");
        mongoTemplate.save(schedule);
    }


   @Scheduled(cron = "0 0 0 * * ?") // 매일 0시 0분 0초에 실행
   public void sendDailyScheduleReminders() {
       log.info("스케줄 알림 배치 작업 시작 at {}", LocalDateTime.now());

       long totalUsers = getTotalUserCount();
       log.info("처리 대상 총 사용자 수: {}", totalUsers);
       
       if (totalUsers == 0) {
           log.info("처리할 사용자가 없습니다.");
           return;
       }

       LocalDateTime now = LocalDateTime.now();
       LocalDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
       final double HIGH_PRIORITY_THRESHOLD = 3.0; // 중요 스케줄 알림 기준 가중치

       int totalBatches = (int) Math.ceil((double) totalUsers / BATCH_SIZE);
       int totalSuccessCount = 0;
       int totalFailureCount = 0;

       for (int batchNumber = 0; batchNumber < totalBatches; batchNumber++) {
           try {
               Page<User> userPage = getUsersInBatch(batchNumber, BATCH_SIZE);
               List<User> users = userPage.getContent();
               
               if (users.isEmpty()) {
                   log.info("배치 {}에 사용자가 없습니다. 배치 처리 종료.", batchNumber + 1);
                   break;
               }

               int batchSuccessCount = 0;
               int batchFailureCount = 0;

               log.info("배치 {}/{} 처리 시작 (사용자 {}명)", batchNumber + 1, totalBatches, users.size());

               for (User user : users) {
                   try {
                       if (user.getEmail() == null || user.getEmail().isEmpty()) {
                           log.warn("사용자 {} ({})의 이메일 주소가 없어 알림을 건너뜁니다.", user.getId(), user.getName());
                           batchFailureCount++;
                           continue;
                       }

                       List<ScheduleListDto> userSchedules = getUserSchedules(user.getId().toString());

                       // 1. 중요도 높은 스케줄 알림 (오늘 또는 내일 시작/마감되는 스케줄 중)
                       List<ScheduleListDto> highPrioritySchedules = userSchedules.stream()
                               .filter(schedule -> schedule.getPriority() != null && schedule.getPriority() >= HIGH_PRIORITY_THRESHOLD)
                               .filter(schedule -> schedule.getStartDate() != null &&
                                       (schedule.getStartDate().toLocalDate().isEqual(startOfToday.toLocalDate()) || // 오늘 시작
                                        schedule.getStartDate().toLocalDate().isEqual(startOfToday.toLocalDate().plusDays(1)))) // 내일 시작
                               .collect(Collectors.toList());
                       
                       boolean sentHighPriorityEmail = false;
                       if (!highPrioritySchedules.isEmpty()) {
                           sortSchedulesForEmail(highPrioritySchedules, user); // 중요도 높은 순, 다음 제목 순으로 정렬
                           String subject = String.format("Priorify: 오늘/내일의 중요 스케줄! (%s)", emailService.todayDate());
                           try {
                               int representativeDaysRemaining = highPrioritySchedules.stream()
                                                                    .mapToInt(s -> (int)ChronoUnit.DAYS.between(startOfToday, s.getStartDate().truncatedTo(ChronoUnit.DAYS)))
                                                                    .min().orElse(0);

                               emailService.sendEmailNotice(user.getEmail(), subject, highPrioritySchedules, representativeDaysRemaining);
                               log.debug("사용자 {} ({})에게 중요 스케줄 알림 이메일 발송 완료 (스케줄 {}개)", user.getId(), user.getName(), highPrioritySchedules.size());
                               sentHighPriorityEmail = true;
                           } catch (MessagingException e) {
                               log.error("사용자 {} ({})에게 중요 스케줄 알림 이메일 발송 실패: {}", user.getId(), user.getName(), e.getMessage());
                               batchFailureCount++;
                               continue;
                           }
                       }

                       // 2. 기존의 마감 임박 스케줄 알림 (0, 1, 3, 7일 후 시작)
                       Map<Integer, List<ScheduleListDto>> remindersByDays = userSchedules.stream()
                               .filter(schedule -> schedule.getStartDate() != null && !schedule.getStartDate().isBefore(startOfToday))
                               .collect(Collectors.groupingBy(schedule -> {
                                   long days = ChronoUnit.DAYS.between(startOfToday, schedule.getStartDate().truncatedTo(ChronoUnit.DAYS));
                                   if (days == 0) return 0; // 오늘 시작
                                   if (days == 1) return 1; // 내일 시작
                                   if (days == 3) return 3; // 3일 후 시작
                                   if (days == 7) return 7; // 7일 후 시작
                                   return -1; // 알림 대상 날짜가 아니면 -1 그룹으로 (필터링되지 않은 경우)
                               }));

                       int[] notificationDays = {0, 1, 3, 7}; // 알림 보낼 남은 날짜 기준
                       boolean sentAnyEmail = sentHighPriorityEmail;
                       
                       for (int days : notificationDays) {
                           List<ScheduleListDto> schedulesToSend = remindersByDays.getOrDefault(days, Collections.emptyList());

                           // 중요도 알림에서 이미 보낸 스케줄 제외 로직 (ID 기반)
                           List<String> highPriorityScheduleIds = highPrioritySchedules.stream().map(ScheduleListDto::getId).collect(Collectors.toList());
                           schedulesToSend = schedulesToSend.stream()
                                                .filter(s -> !highPriorityScheduleIds.contains(s.getId()))
                                                .collect(Collectors.toList());

                           if (!schedulesToSend.isEmpty()) {
                               sortSchedulesForEmail(schedulesToSend, user);
                               String subject = String.format("Priorify 스케줄 알림: %s 시작", (days == 0 ? "오늘" : (days == 1 ? "내일" : days + "일 후")));
                               try {
                                   emailService.sendEmailNotice(user.getEmail(), subject, schedulesToSend, days);
                                   log.debug("사용자 {} ({})에게 {}일 후 스케줄 알림 이메일 발송 완료 (스케줄 {}개)", user.getId(), user.getName(), days, schedulesToSend.size());
                                   sentAnyEmail = true;
                               } catch (javax.mail.MessagingException e) { 
                                   log.error("사용자 {} ({})에게 {}일 후 스케줄 알림 이메일 발송 실패: {}", user.getId(), user.getName(), days, e.getMessage());
                                   batchFailureCount++;
                               }
                           }
                       }
                       
                       if (sentAnyEmail) {
                           batchSuccessCount++;
                       } else {
                           batchSuccessCount++; // 보낼 스케줄이 없는 것도 정상 처리로 간주
                       }
                       
                   } catch (Exception e) { 
                       log.error("사용자 {} ({})에게 스케줄 알림 중 예상치 못한 오류 발생: {}", user.getId(), user.getName(), e.getMessage());
                       batchFailureCount++;
                   }
               }

               totalSuccessCount += batchSuccessCount;
               totalFailureCount += batchFailureCount;
               
               logBatchStatistics(batchNumber + 1, users.size(), batchSuccessCount, batchFailureCount, totalUsers);

               // 마지막 배치가 아니면 지연 시간 추가
               if (batchNumber < totalBatches - 1) {
                   waitBetweenBatches();
               }

           } catch (Exception e) {
               log.error("배치 {} 처리 중 치명적 오류 발생: {}", batchNumber + 1, e.getMessage());
               totalFailureCount += BATCH_SIZE; // 배치 전체를 실패로 처리
           }
       }
       
       log.info("스케줄 알림 배치 작업 완료 - 총 성공: {}, 총 실패: {}, 총 사용자: {}", 
               totalSuccessCount, totalFailureCount, totalUsers);
   }



    /**
     * 알림 이메일 발송을 위한 스케줄 목록 정렬 로직
     * 정렬 기준:
     * 1. 중요도(priority) 높은 순 (내림차순)
     * 2. 제목 알파벳 순 (오름차순)
     * 이 메소드는 sendDailyScheduleReminders에서 호출되며, 이미 특정 남은 날짜 그룹에 속한 스케줄 리스트를 받습니다.
     */
    private void sortSchedulesForEmail(List<ScheduleListDto> schedules, User user) {
        schedules.sort(Comparator
                // 1. 중요도(priority) 내림차순 (null 처리: null은 낮은 우선순위로)
                .comparing(ScheduleListDto::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                // 2. 제목 알파벳 순 오름차순 (null 처리 포함)
                .thenComparing(schedule -> schedule.getTitle() != null ? schedule.getTitle() : "", Comparator.naturalOrder())
        );
    }

    /**
     * 상위 우선순위 스케줄 알림 발송 (배치 처리)
     * getCategoryScheduleAggregation과 동일한 가중치 알고리즘 사용
     */
    @Scheduled(cron = "0 0 9 * * ?") // 매일 오전 9시에 실행
    public void sendTopPriorityScheduleReminders() {
        log.info("상위 우선순위 스케줄 알림 배치 작업 시작 at {}", LocalDateTime.now());

        long totalUsers = getTotalUserCount();
        log.info("처리 대상 총 사용자 수: {}", totalUsers);
        
        if (totalUsers == 0) {
            log.info("처리할 사용자가 없습니다.");
            return;
        }

        int totalBatches = (int) Math.ceil((double) totalUsers / BATCH_SIZE);
        int totalSuccessCount = 0;
        int totalFailureCount = 0;

        for (int batchNumber = 0; batchNumber < totalBatches; batchNumber++) {
            try {
                Page<User> userPage = getUsersInBatch(batchNumber, BATCH_SIZE);
                List<User> users = userPage.getContent();
                
                if (users.isEmpty()) {
                    log.info("배치 {}에 사용자가 없습니다. 배치 처리 종료.", batchNumber + 1);
                    break;
                }

                int batchSuccessCount = 0;
                int batchFailureCount = 0;

                log.info("배치 {}/{} 처리 시작 (사용자 {}명)", batchNumber + 1, totalBatches, users.size());

                for (User user : users) {
                    try {
                        if (user.getEmail() == null || user.getEmail().isEmpty()) {
                            log.warn("사용자 {} ({})의 이메일 주소가 없어 상위 우선순위 알림을 건너뜁니다.", user.getId(), user.getName());
                            batchFailureCount++;
                            continue;
                        }

                        // 사용자의 상위 2개 우선순위 스케줄 조회
                        List<ScheduleListDto> topPrioritySchedules = getTopPrioritySchedulesWithWeights(user.getId().toString(), user, 2);
                        
                        if (!topPrioritySchedules.isEmpty()) {
                            emailService.sendTopPriorityScheduleNotice(user.getEmail(), user.getName(), topPrioritySchedules);
                            log.debug("사용자 {} ({})에게 상위 우선순위 스케줄 알림 이메일 발송 완료 (스케줄 {}개)", 
                                    user.getId(), user.getName(), topPrioritySchedules.size());
                            batchSuccessCount++;
                        } else {
                            log.debug("사용자 {} ({})에게 발송할 상위 우선순위 스케줄이 없습니다.", user.getId(), user.getName());
                            batchSuccessCount++; // 성공으로 카운트 (스케줄이 없는 것은 정상 상황)
                        }
                    } catch (MessagingException e) {
                        log.error("사용자 {} ({})에게 상위 우선순위 스케줄 알림 이메일 발송 실패: {}", user.getId(), user.getName(), e.getMessage());
                        batchFailureCount++;
                    } catch (Exception e) {
                        log.error("사용자 {} ({})에게 상위 우선순위 스케줄 알림 중 예상치 못한 오류 발생: {}", user.getId(), user.getName(), e.getMessage());
                        batchFailureCount++;
                    }
                }

                totalSuccessCount += batchSuccessCount;
                totalFailureCount += batchFailureCount;
                
                logBatchStatistics(batchNumber + 1, users.size(), batchSuccessCount, batchFailureCount, totalUsers);

                // 마지막 배치가 아니면 지연 시간 추가
                if (batchNumber < totalBatches - 1) {
                    waitBetweenBatches();
                }

            } catch (Exception e) {
                log.error("배치 {} 처리 중 치명적 오류 발생: {}", batchNumber + 1, e.getMessage());
                totalFailureCount += BATCH_SIZE; // 배치 전체를 실패로 처리
            }
        }
        
        log.info("상위 우선순위 스케줄 알림 배치 작업 완료 - 총 성공: {}, 총 실패: {}, 총 사용자: {}", 
                totalSuccessCount, totalFailureCount, totalUsers);
    }

    /**
     * getCategoryScheduleAggregation과 동일한 알고리즘으로 상위 우선순위 스케줄 조회
     */
    public List<ScheduleListDto> getTopPrioritySchedulesWithWeights(String userId, User user, int limit) {
        // MongoDB Aggregation Pipeline - getCategoryScheduleAggregation과 동일한 가중치 계산 로직
        List<Document> pipeline = Arrays.asList(
            // Stage 1: 사용자의 활성 스케줄 필터링 (오늘부터 7일 이내)
            new Document("$match", new Document()
                .append("$and", Arrays.asList(
                    new Document("startAt", new Document("$gte", LocalDateTime.now())),
                    new Document("startAt", new Document("$lte", LocalDateTime.now().plusDays(7))),
                    new Document("userId", new ObjectId(userId)),
                    new Document("$or", Arrays.asList(
                        new Document("status", "active"),
                        new Document("status", "completed")
                    ))
                ))),
            
            // Stage 2: 원본 카테고리 보존 및 카테고리 unwind
            new Document("$addFields", new Document()
                .append("originalCategories", "$categories")),
            new Document("$unwind", new Document()
                .append("path", "$categories")
                .append("preserveNullAndEmptyArrays", true)),
            
            // Stage 3: 긴급도 및 카테고리 가중치 계산 (getCategoryScheduleAggregation과 동일)
            new Document("$addFields", new Document()
                .append("urgencyScore", createUrgencyScoreExpression())
                .append("categoryWeight", createCategoryWeightExpression(user))),
            
            // Stage 4: 최종 우선순위 계산
            new Document("$addFields", new Document()
                .append("priority", new Document("$multiply", Arrays.asList("$urgencyScore", "$categoryWeight")))),
            
            // Stage 5: 카테고리별로 그룹화하여 최고 우선순위만 유지 (중복 제거)
            new Document("$group", new Document()
                .append("_id", "$_id")
                .append("title", new Document("$first", "$title"))
                .append("startAt", new Document("$first", "$startAt"))
                .append("endAt", new Document("$first", "$endAt"))
                .append("status", new Document("$first", "$status"))
                .append("originalCategories", new Document("$first", "$originalCategories"))
                .append("maxPriority", new Document("$max", "$priority"))),
            
            // Stage 6: 우선순위 높은 순으로 정렬
            new Document("$sort", new Document("maxPriority", -1)),
            
            // Stage 7: 상위 limit개만 선택
            new Document("$limit", limit),
            
            // Stage 8: 필요한 필드만 투영
            new Document("$project", new Document()
                .append("_id", new Document("$toString", "$_id"))
                .append("title", "$title")
                .append("startAt", "$startAt")
                .append("endAt", "$endAt")
                .append("status", "$status")
                .append("originalCategories", "$originalCategories")
                .append("priority", "$maxPriority"))
        );

        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection("schedules")
                .aggregate(pipeline, Document.class)
                .into(results);
        
        return results.stream()
                .map(doc -> {
                    List<String> categories = doc.getList("originalCategories", String.class);
                    return createScheduleListDto(doc, categories);
                })
                .collect(Collectors.toList());
    }

    /**
     * 배치 처리를 위한 헬퍼 메서드들
     */
    
    /**
     * 전체 사용자 수 조회
     */
    private long getTotalUserCount() {
        return userService.count();
    }
    
    /**
     * 페이지네이션으로 사용자 조회
     */
    private Page<User> getUsersInBatch(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userService.findAll(pageable);
    }
    
    /**
     * 배치 처리 간 지연
     */
    private void waitBetweenBatches() {
        try {
            Thread.sleep(BATCH_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("배치 처리 지연 중 인터럽트 발생: {}", e.getMessage());
        }
    }
    
    /**
     * 배치별 통계 정보
     */
    private void logBatchStatistics(int batchNumber, int batchSize, int successCount, int failureCount, long totalUsers) {
        log.info("배치 {}/{} 처리 완료 - 성공: {}, 실패: {}, 배치 크기: {}", 
                batchNumber, 
                (int) Math.ceil((double) totalUsers / BATCH_SIZE), 
                successCount, 
                failureCount, 
                batchSize);
    }
}


