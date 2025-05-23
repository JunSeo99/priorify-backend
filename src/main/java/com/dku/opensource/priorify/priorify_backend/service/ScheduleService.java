package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import com.dku.opensource.priorify.priorify_backend.model.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Service
public class ScheduleService {

    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    private final PriorityService priorityService;

    public ScheduleService(MongoTemplate mongoTemplate, UserService userService, PriorityService priorityService) {
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
        this.priorityService = priorityService;
    }

    // /**
    //  * 일정을 생성하고 우선순위를 계산하여 저장합니다.
    //  */
    // public Schedule createSchedule(String userId, Schedule schedule) {
    //     // 사용자 확인
    //     Optional<User> user = userService.findById(new ObjectId(userId));
    //     if (!user.isPresent()) {
    //         throw new RuntimeException("사용자를 찾을 수 없습니다.");
    //     }
        
    //     // 기본 값 설정
    //     if (schedule.getUserId() == null) {
    //         schedule.setUserId(userId);
    //     }
        
    //     if (schedule.getStatus() == null) {
    //         schedule.setStatus("active");
    //     }
        
    //     if (schedule.getSource() == null) {
    //         schedule.setSource("priorify");
    //     }
        
    //     if (schedule.getConnections() == null) {
    //         schedule.setConnections(new ArrayList<>());
    //     }
        
    //     // 우선순위 벡터 계산 및 설정
    //     List<Double> priorityVector = priorityService.calculatePriorityVector(
    //         schedule.getCategory(), 
    //         user.get(), 
    //         schedule.getStartAt(), 
    //         schedule.getEndAt()
    //     );
    //     schedule.setPriorityVector(priorityVector);
        
    //     // 현재 시간 설정
    //     LocalDateTime now = LocalDateTime.now();
    //     schedule.setCreatedAt(now);
    //     schedule.setUpdatedAt(now);
        
    //     // MongoDB에 저장
    //     return mongoTemplate.save(schedule);
    // }

    // /**
    //  * 일정을 업데이트하고 우선순위를 재계산합니다.
    //  */
    // public Schedule updateSchedule(String userId, String scheduleId, Schedule updatedSchedule) {
    //     // 기존 일정 조회
    //     Schedule existingSchedule = findScheduleByIdAndUser(scheduleId, userId);
    //     if (existingSchedule == null) {
    //         throw new RuntimeException("일정을 찾을 수 없습니다.");
    //     }

    //     // 업데이트할 필드 설정
    //     existingSchedule.setTitle(updatedSchedule.getTitle());
    //     existingSchedule.setCategory(updatedSchedule.getCategory());
    //     existingSchedule.setStartAt(updatedSchedule.getStartAt());
    //     existingSchedule.setEndAt(updatedSchedule.getEndAt());
        
    //     // 선택적 필드 업데이트
    //     if (updatedSchedule.getStatus() != null) {
    //         existingSchedule.setStatus(updatedSchedule.getStatus());
    //     }
        
    //     if (updatedSchedule.getConnections() != null) {
    //         existingSchedule.setConnections(updatedSchedule.getConnections());
    //     }

    //     if (updatedSchedule.getSource() != null) {
    //         existingSchedule.setSource(updatedSchedule.getSource());
    //     }
        
    //     if (updatedSchedule.getExternalEventId() != null) {
    //         existingSchedule.setExternalEventId(updatedSchedule.getExternalEventId());
    //     }
        
    //     // 우선순위 벡터 재계산
    //     Optional<User> user = userService.findById(new ObjectId(userId));
    //     if (!user.isPresent()) {
    //         throw new RuntimeException("사용자를 찾을 수 없습니다.");
    //     }
        
    //     List<Double> priorityVector = priorityService.calculatePriorityVector(
    //         existingSchedule.getCategory(), 
    //         user.get(), 
    //         existingSchedule.getStartAt(), 
    //         existingSchedule.getEndAt()
    //     );
    //     existingSchedule.setPriorityVector(priorityVector);
        
    //     // 업데이트 시간 갱신
    //     existingSchedule.setUpdatedAt(LocalDateTime.now());

    //     return mongoTemplate.save(existingSchedule);
    // }

    // /**
    //  * 일정을 삭제합니다.
    //  */
    // public void deleteSchedule(String userId, String scheduleId) {
    //     Schedule schedule = findScheduleByIdAndUser(scheduleId, userId);
    //     if (schedule == null) {
    //         throw new RuntimeException("일정을 찾을 수 없습니다.");
    //     }
    //     mongoTemplate.remove(schedule);
    // }

    // /**
    //  * 특정 ID와 사용자로 일정을 조회합니다.
    //  */
    // public Schedule findScheduleByIdAndUser(String scheduleId, String userId) {
    //     Query query = new Query(Criteria.where("id").is(scheduleId).and("userId").is(userId));
    //     return mongoTemplate.findOne(query, Schedule.class);
    // }

    // /**
    //  * 사용자의 모든 일정을 조회합니다.
    //  */
    // public List<Schedule> findAllSchedulesByUser(String userId) {
    //     Query query = new Query(Criteria.where("userId").is(userId));
    //     return mongoTemplate.find(query, Schedule.class);
    // }

    // /**
    //  * 날짜 범위로 일정을 조회합니다.
    //  */
    // public List<Schedule> findSchedulesByDateRange(ObjectId userId, LocalDateTime start, LocalDateTime end) {
    //     Query query = new Query(Criteria.where("userId").is(userId)
    //             .and("startAt").lte(end)
    //             .and("endAt").gte(start));
    //     return mongoTemplate.find(query, Schedule.class);
    // }

    // /**
    //  * 일정의 상태를 토글합니다. (active <-> completed)
    //  */
    // public Schedule toggleScheduleStatus(String userId, String scheduleId) {
    //     Schedule schedule = findScheduleByIdAndUser(scheduleId, userId);
    //     if (schedule == null) {
    //         throw new RuntimeException("일정을 찾을 수 없습니다.");
    //     }
        
    //     // "active" <-> "completed" 상태 토글
    //     String newStatus = "active".equals(schedule.getStatus()) ? "completed" : "active";
    //     schedule.setStatus(newStatus);
    //     schedule.setUpdatedAt(LocalDateTime.now());
        
    //     return mongoTemplate.save(schedule);
    // }
    
    // /**
    //  * 특정 소스의 일정을 조회합니다.
    //  */
    // public List<Schedule> findSchedulesBySource(String userId, String source) {
    //     Query query = new Query(Criteria.where("userId").is(userId)
    //             .and("source").is(source));
    //     return mongoTemplate.find(query, Schedule.class);
    // }
    
    // /**
    //  * 우선순위별로 일정을 분류합니다.
    //  */
    // public Map<String, List<Schedule>> categorizeSchedulesByPriority(String userId, LocalDateTime start, LocalDateTime end) {
    //     List<Schedule> schedules = findSchedulesByDateRange(new ObjectId(userId), start, end);
    //     Map<String, List<Schedule>> result = new HashMap<>();
        
    //     List<Schedule> highPriority = new ArrayList<>();
    //     List<Schedule> mediumPriority = new ArrayList<>();
    //     List<Schedule> lowPriority = new ArrayList<>();
        
    //     for (Schedule schedule : schedules) {
    //         if (schedule.getPriorityVector() == null || schedule.getPriorityVector().isEmpty()) {
    //             mediumPriority.add(schedule);
    //             continue;
    //         }
            
    //         double priorityScore = schedule.getPriorityVector().get(0);
            
    //         if (priorityScore >= 0.7) {
    //             highPriority.add(schedule);
    //         } else if (priorityScore >= 0.4) {
    //             mediumPriority.add(schedule);
    //         } else {
    //             lowPriority.add(schedule);
    //         }
    //     }
        
    //     result.put("high", highPriority);
    //     result.put("medium", mediumPriority);
    //     result.put("low", lowPriority);
        
    //     return result;
    // }
    
    // /**
    //  * 여러 일정의 우선순위 벡터를 일괄 업데이트합니다.
    //  */
    // public void batchUpdatePriorityVectors(String userId) {
    //     List<Schedule> schedules = findAllSchedulesByUser(userId);
    //     Optional<User> userOpt = userService.findById(new ObjectId(userId));
        
    //     if (!userOpt.isPresent()) {
    //         throw new RuntimeException("사용자를 찾을 수 없습니다.");
    //     }
        
    //     User user = userOpt.get();
    //     LocalDateTime now = LocalDateTime.now();
        
    //     for (Schedule schedule : schedules) {
    //         List<Double> priorityVector = priorityService.calculatePriorityVector(
    //             schedule.getCategory(), 
    //             user, 
    //             schedule.getStartAt(), 
    //             schedule.getEndAt()
    //         );
            
    //         schedule.setPriorityVector(priorityVector);
    //         schedule.setUpdatedAt(now);
    //         mongoTemplate.save(schedule);
    //     }
    // }
} 