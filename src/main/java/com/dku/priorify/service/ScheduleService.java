package com.dku.priorify.service;

import com.dku.priorify.model.Schedule;
import com.dku.priorify.model.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    public Schedule createSchedule(ObjectId userId, Schedule schedule) {
        Optional<User> user = userService.findById(userId);
        if (!user.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        schedule.setUser(user.get());
        
        // 우선순위 점수 계산
        double priorityScore = priorityService.calculatePriorityScore(schedule.getCategory(), user.get().getName());
        schedule.setPriorityScore(priorityScore);
        
        return mongoTemplate.save(schedule);
    }

    public Schedule updateSchedule(ObjectId userId, String scheduleId, Schedule updatedSchedule) {
        Schedule existingSchedule = findScheduleByIdAndUser(scheduleId, userId);
        if (existingSchedule == null) {
            throw new RuntimeException("일정을 찾을 수 없습니다.");
        }

        existingSchedule.setTitle(updatedSchedule.getTitle());
        existingSchedule.setDescription(updatedSchedule.getDescription());
        existingSchedule.setDatetime(updatedSchedule.getDatetime());
        existingSchedule.setCategory(updatedSchedule.getCategory());
        existingSchedule.setImportance(updatedSchedule.getImportance());

        // 우선순위 점수 재계산
        Optional<User> user = userService.findById(userId);
        if (!user.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        double priorityScore = priorityService.calculatePriorityScore(existingSchedule.getCategory(), user.get().getName());
        existingSchedule.setPriorityScore(priorityScore);

        return mongoTemplate.save(existingSchedule);
    }

    public void deleteSchedule(ObjectId userId, String scheduleId) {
        Schedule schedule = findScheduleByIdAndUser(scheduleId, userId);
        if (schedule == null) {
            throw new RuntimeException("일정을 찾을 수 없습니다.");
        }
        mongoTemplate.remove(schedule);
    }

    public Schedule findScheduleByIdAndUser(String scheduleId, ObjectId userId) {
        Query query = new Query(Criteria.where("id").is(scheduleId).and("user.$id").is(userId));
        return mongoTemplate.findOne(query, Schedule.class);
    }

    public List<Schedule> findAllSchedulesByUser(ObjectId userId) {
        Query query = new Query(Criteria.where("user.$id").is(userId));
        return mongoTemplate.find(query, Schedule.class);
    }

    public List<Schedule> findSchedulesByDateRange(ObjectId userId, LocalDateTime start, LocalDateTime end) {
        Query query = new Query(Criteria.where("user.$id").is(userId)
                .and("datetime").gte(start).lte(end));
        return mongoTemplate.find(query, Schedule.class);
    }

    public void toggleScheduleCompletion(ObjectId userId, String scheduleId) {
        Schedule schedule = findScheduleByIdAndUser(scheduleId, userId);
        if (schedule == null) {
            throw new RuntimeException("일정을 찾을 수 없습니다.");
        }
        schedule.setCompleted(!schedule.isCompleted());
        mongoTemplate.save(schedule);
    }
} 