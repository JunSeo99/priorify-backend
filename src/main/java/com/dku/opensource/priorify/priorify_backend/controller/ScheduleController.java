package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.ScheduleService;
import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
        // Schedule createdSchedule = scheduleService.createSchedule(userId, schedule);
        return ResponseEntity.ok(null);
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId,
            @Valid @RequestBody Schedule schedule) {
        String userId = (String) request.getAttribute("userId");
        // Schedule updatedSchedule = scheduleService.updateSchedule(userId, scheduleId, schedule);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        // scheduleService.deleteSchedule(userId, scheduleId);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        // Schedule schedule = scheduleService.findScheduleByIdAndUser(scheduleId, userId);
        // if (schedule == null) {
        //     return ResponseEntity.notFound().build();
        // }
        return ResponseEntity.ok(null);
    }

    @GetMapping
    public ResponseEntity<List<Schedule>> getAllSchedules(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        // List<Schedule> schedules = scheduleService.findAllSchedulesByUser(userId);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/range")
    public ResponseEntity<List<Schedule>> getSchedulesByDateRange(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String userId = (String) request.getAttribute("userId");
        // List<Schedule> schedules = scheduleService.findSchedulesByDateRange(new ObjectId(userId), start, end);
        return ResponseEntity.ok(null);
    }

    @PatchMapping("/{scheduleId}/toggle-completion")
    public ResponseEntity<?> toggleScheduleCompletion(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        // scheduleService.toggleScheduleStatus(userId, scheduleId);
        return ResponseEntity.ok(null);
    }
} 