package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.dto.PriorityDto;
import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;
import com.dku.opensource.priorify.priorify_backend.model.User;
import com.mongodb.client.result.UpdateResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.apache.http.HttpStatus;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class PriorityService {
    
    @Autowired
    private MongoTemplate mongoTemplate;

    private final UserService userService;

    public PriorityService(UserService userService) {
        this.userService = userService;
    }


    //한번에 처리

    public ResponseEntity<?> setPriorities(ObjectId userId, PriorityDto priorityDto) {
        Update update = new Update();
        update.set("highPriorities", priorityDto.getHighPriorities());
        update.set("lowPriorities", priorityDto.getLowPriorities());
        UpdateResult result = mongoTemplate.updateFirst(
            new Query(Criteria.where("id").is(userId)),
            update,
            User.class
        );

        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }

        return ResponseEntity.ok("우선순위가 설정되었습니다.");
    }
    public PriorityDto getPriorities(String userId) {
        Optional<User> userOpt = userService.findById(new ObjectId(userId));
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        User user = userOpt.get();
        return PriorityDto.builder()
            .highPriorities(new ArrayList<>(user.getHighPriorities()))
            .lowPriorities(new ArrayList<>(user.getLowPriorities()))
            .build();
    }

    
} 