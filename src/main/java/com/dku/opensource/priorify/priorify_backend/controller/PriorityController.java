package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.PriorityService;
import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.bson.types.ObjectId;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/priorities")
public class PriorityController {

    private final PriorityService priorityService;

    public PriorityController(PriorityService priorityService) {
        this.priorityService = priorityService;
    }

    @PostMapping("/high")
    public ResponseEntity<?> setHighPriorities(
            HttpServletRequest request,
            @Valid @RequestBody List<CategoryPriority> priorities) {
        String userId = (String) request.getAttribute("userId");
        priorityService.setHighPriorities(new ObjectId(userId), priorities);
        return ResponseEntity.ok("상위 우선순위가 설정되었습니다.");
    }

    @PostMapping("/low")
    public ResponseEntity<?> setLowPriorities(
            HttpServletRequest request,
            @Valid @RequestBody List<CategoryPriority> priorities) {
        String userId = (String) request.getAttribute("userId");
        priorityService.setLowPriorities(new ObjectId(userId), priorities);
        return ResponseEntity.ok("하위 우선순위가 설정되었습니다.");
    }

    @GetMapping("/high")
    public ResponseEntity<List<CategoryPriority>> getHighPriorities(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ResponseEntity.ok(priorityService.getHighPriorities(userId));
    }

    @GetMapping("/low")
    public ResponseEntity<List<CategoryPriority>> getLowPriorities(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ResponseEntity.ok(priorityService.getLowPriorities(userId));
    }
} 