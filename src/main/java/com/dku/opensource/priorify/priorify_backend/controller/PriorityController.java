package com.dku.opensource.priorify.priorify_backend.controller;

import com.dku.opensource.priorify.priorify_backend.service.EmailService;
import com.dku.opensource.priorify.priorify_backend.service.PriorityService;
import com.dku.opensource.priorify.priorify_backend.dto.PriorityDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.bson.types.ObjectId;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/priorities")
public class PriorityController {

    private final PriorityService priorityService;

    public PriorityController(PriorityService priorityService) {
        this.priorityService = priorityService;
    }

    // 상위, 하위 통합하는게 나을듯.. -> 완료

    @PostMapping
    public ResponseEntity<?> setPriorities(
            HttpServletRequest request,
            @Valid @RequestBody PriorityDto priorityDto) {
        String userId = (String) request.getAttribute("userId");
        priorityService.setPriorities(new ObjectId(userId), priorityDto);
        return ResponseEntity.ok("우선순위가 설정되었습니다.");
    }

    @GetMapping
    public ResponseEntity<PriorityDto> getPriorities(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ResponseEntity.ok(priorityService.getPriorities(userId));
    }

} 