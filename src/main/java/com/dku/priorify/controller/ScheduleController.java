package com.dku.priorify.controller;

import com.dku.priorify.model.Schedule;
import com.dku.priorify.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.bson.types.ObjectId;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<Schedule> createSchedule(
            HttpServletRequest request,
            @Valid @RequestBody Schedule schedule) {
        String userId = (String) request.getAttribute("userId");
        Schedule createdSchedule = scheduleService.createSchedule(new ObjectId(userId), schedule);
        return ResponseEntity.ok(createdSchedule);
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId,
            @Valid @RequestBody Schedule schedule) {
        String userId = (String) request.getAttribute("userId");
        Schedule updatedSchedule = scheduleService.updateSchedule(new ObjectId(userId), scheduleId, schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        scheduleService.deleteSchedule(new ObjectId(userId), scheduleId);
        return ResponseEntity.ok("일정이 삭제되었습니다.");
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        Schedule schedule = scheduleService.findScheduleByIdAndUser(scheduleId, new ObjectId(userId));
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(schedule);
    }

    @GetMapping
    public ResponseEntity<List<Schedule>> getAllSchedules(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<Schedule> schedules = scheduleService.findAllSchedulesByUser(new ObjectId(userId));
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/range")
    public ResponseEntity<List<Schedule>> getSchedulesByDateRange(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String userId = (String) request.getAttribute("userId");
        List<Schedule> schedules = scheduleService.findSchedulesByDateRange(new ObjectId(userId), start, end);
        return ResponseEntity.ok(schedules);
    }

    @PatchMapping("/{scheduleId}/toggle-completion")
    public ResponseEntity<?> toggleScheduleCompletion(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        scheduleService.toggleScheduleCompletion(new ObjectId(userId), scheduleId);
        return ResponseEntity.ok("일정 완료 상태가 변경되었습니다.");
    }
} 