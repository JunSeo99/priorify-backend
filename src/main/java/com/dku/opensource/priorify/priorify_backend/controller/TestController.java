package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.dto.ScheduleListDto;
import com.dku.opensource.priorify.priorify_backend.service.ScheduleService;
import com.dku.opensource.priorify.priorify_backend.service.EmailService;
import com.dku.opensource.priorify.priorify_backend.service.UserService;
import com.dku.opensource.priorify.priorify_backend.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import javax.mail.MessagingException;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final ScheduleService scheduleService;
    private final EmailService emailService;
    private final UserService userService;

    /**
     * 특정 사용자의 상위 우선순위 스케줄 조회 API
     * GET /api/test/top-priority-schedules/{userId}
     */
    @GetMapping("/top-priority-schedules/{userId}")
    public ResponseEntity<Map<String, Object>> getTopPrioritySchedules(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userService.findById(new ObjectId(userId));
            if (!userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "사용자를 찾을 수 없습니다: " + userId);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userOpt.get();
            
            // 정확한 가중치 알고리즘을 사용하여 상위 우선순위 스케줄 조회
            List<ScheduleListDto> topSchedules = scheduleService.getTopPrioritySchedulesWithWeights(userId, user, 2);
            List<ScheduleListDto> allSchedules = scheduleService.getUserSchedules(userId);

            response.put("success", true);
            response.put("userId", userId);
            response.put("userName", user.getName());
            response.put("topSchedules", topSchedules);
            response.put("totalSchedules", allSchedules.size());
            response.put("algorithm", "getCategoryScheduleAggregation 동일 알고리즘 사용");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("상위 우선순위 스케줄 조회 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "스케줄 조회 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 특정 사용자에게 상위 우선순위 스케줄 알림 메일 즉시 발송 API
     * POST /api/test/send-top-priority-email/{userId}
     */
    @PostMapping("/send-top-priority-email/{userId}")
    public ResponseEntity<Map<String, Object>> sendTopPriorityEmail(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userService.findById(new ObjectId(userId));
            if (!userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "사용자를 찾을 수 없습니다: " + userId);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userOpt.get();
            
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                response.put("success", false);
                response.put("message", "사용자의 이메일 주소가 설정되어 있지 않습니다.");
                return ResponseEntity.badRequest().body(response);
            }

            // 정확한 가중치 알고리즘을 사용하여 상위 우선순위 스케줄 조회
            List<ScheduleListDto> topSchedules = scheduleService.getTopPrioritySchedulesWithWeights(userId, user, 2);

            if (topSchedules.isEmpty()) {
                response.put("success", false);
                response.put("message", "발송할 상위 우선순위 스케줄이 없습니다.");
                return ResponseEntity.ok(response);
            }

            // 이메일 발송
            emailService.sendTopPriorityScheduleNotice(user.getEmail(), user.getName(), topSchedules);
            
            response.put("success", true);
            response.put("message", "상위 우선순위 스케줄 알림 이메일이 성공적으로 발송되었습니다.");
            response.put("userId", userId);
            response.put("userName", user.getName());
            response.put("userEmail", user.getEmail());
            response.put("schedulesCount", topSchedules.size());
            response.put("schedules", topSchedules);
            response.put("algorithm", "getCategoryScheduleAggregation 동일 알고리즘 사용");
            
            return ResponseEntity.ok(response);
            
        } catch (MessagingException e) {
            log.error("이메일 발송 실패: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "이메일 발송에 실패했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("상위 우선순위 이메일 발송 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "이메일 발송 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 모든 사용자에게 상위 우선순위 스케줄 알림 발송 (테스트용)
     * POST /api/test/send-all-top-priority-emails
     */
    @PostMapping("/send-all-top-priority-emails")
    public ResponseEntity<Map<String, Object>> sendAllTopPriorityEmails() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 기존 스케줄 서비스의 메서드를 직접 호출
            scheduleService.sendTopPriorityScheduleReminders();
            
            response.put("success", true);
            response.put("message", "모든 사용자에게 상위 우선순위 스케줄 알림 발송 작업이 시작되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("전체 상위 우선순위 이메일 발송 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "이메일 발송 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 사용자 목록 조회 API (테스트용)
     * GET /api/test/users
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<User> users = userService.findAll();
            
            List<Map<String, Object>> userSummaries = users.stream()
                    .map(user -> {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("id", user.getId().toString());
                        userInfo.put("name", user.getName());
                        userInfo.put("email", user.getEmail());
                        userInfo.put("hasEmail", user.getEmail() != null && !user.getEmail().isEmpty());
                        return userInfo;
                    })
                    .collect(java.util.stream.Collectors.toList());

            response.put("success", true);
            response.put("totalUsers", users.size());
            response.put("users", userSummaries);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("사용자 목록 조회 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "사용자 목록 조회 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 일반 스케줄 알림 테스트 API
     * POST /api/test/send-daily-reminders
     */
    @PostMapping("/send-daily-reminders")
    public ResponseEntity<Map<String, Object>> sendDailyReminders() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            scheduleService.sendDailyScheduleReminders();
            
            response.put("success", true);
            response.put("message", "일반 스케줄 알림 발송 작업이 시작되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("일반 스케줄 알림 발송 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "알림 발송 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 배치 처리 통계 조회 API (테스트용)
     * GET /api/test/batch-statistics
     */
    @GetMapping("/batch-statistics")
    public ResponseEntity<Map<String, Object>> getBatchStatistics() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<User> allUsers = userService.findAll();
            long totalUsers = allUsers.size();
            int batchSize = 5; // ScheduleService의 BATCH_SIZE와 동일
            int totalBatches = (int) Math.ceil((double) totalUsers / batchSize);
            
            long usersWithEmail = allUsers.stream()
                    .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                    .count();
            
            response.put("success", true);
            response.put("totalUsers", totalUsers);
            response.put("usersWithEmail", usersWithEmail);
            response.put("usersWithoutEmail", totalUsers - usersWithEmail);
            response.put("batchSize", batchSize);
            response.put("totalBatches", totalBatches);
            response.put("estimatedProcessingTime", totalBatches + " seconds (1초 지연 × " + totalBatches + " 배치)");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("배치 통계 조회 중 오류 발생: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "배치 통계 조회 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 