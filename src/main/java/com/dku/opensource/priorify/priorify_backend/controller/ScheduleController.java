package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.ScheduleService;
import com.dku.opensource.priorify.priorify_backend.dto.ScheduleGraphResponseDto;
import com.dku.opensource.priorify.priorify_backend.dto.ScheduleListDto;
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

    /**
     * 사용자의 스케줄 Node Graph 조회
     */
    @GetMapping("/graph")
    public ResponseEntity<ScheduleGraphResponseDto> getScheduleGraph(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days
    ) {
        String userId = (String) request.getAttribute("userId");
        ScheduleGraphResponseDto graph = scheduleService.getScheduleGraph(userId, days);
        return ResponseEntity.ok(graph);
    }

    /**
     * 스케줄 생성
     */
    @PostMapping
    public ResponseEntity<Schedule> createSchedule(
            HttpServletRequest request,
            @Valid @RequestBody Schedule schedule) {
        String userId = (String) request.getAttribute("userId");
        schedule.setUserId(new ObjectId(userId));
        Schedule createdSchedule = scheduleService.createSchedule(schedule);
        return ResponseEntity.ok(createdSchedule);
    }

    /**
     * 스케줄 업데이트
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId,
            @Valid @RequestBody Schedule schedule) {
        String userId = (String) request.getAttribute("userId");
        
        // 기존 스케줄 조회 및 권한 확인
        Schedule existingSchedule = scheduleService.getScheduleById(scheduleId);
        if (existingSchedule == null || !existingSchedule.getUserId().toString().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        
        schedule.setId(scheduleId);
        schedule.setUserId(new ObjectId(userId));
        Schedule updatedSchedule = scheduleService.updateSchedule(schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    /**
     * 스케줄 삭제
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        
        // 기존 스케줄 조회 및 권한 확인
        Schedule existingSchedule = scheduleService.getScheduleById(scheduleId);
        if (existingSchedule == null || !existingSchedule.getUserId().toString().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        
        // soft delete (상태를 inactive로 변경)
        existingSchedule.setStatus("inactive");
        scheduleService.updateSchedule(existingSchedule);
        
        return ResponseEntity.ok().build();
    }

    /**
     * 사용자의 모든 스케줄 조회
     */
    @GetMapping
    public ResponseEntity<List<ScheduleListDto>> getUserSchedules(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<ScheduleListDto> schedules = scheduleService.getUserSchedules(userId);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 특정 스케줄 상세 조회
     */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getSchedule(
            HttpServletRequest request,
            @PathVariable String scheduleId) {
        String userId = (String) request.getAttribute("userId");
        
        Schedule schedule = scheduleService.getScheduleById(scheduleId);
        if (schedule == null || !schedule.getUserId().toString().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(schedule);
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