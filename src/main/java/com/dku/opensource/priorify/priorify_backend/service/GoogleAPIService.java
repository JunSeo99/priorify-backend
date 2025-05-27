package com.dku.opensource.priorify.priorify_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.dku.opensource.priorify.priorify_backend.dto.CalendarSyncResultDto;
import com.dku.opensource.priorify.priorify_backend.dto.EmbeddingRequest;
import com.dku.opensource.priorify.priorify_backend.dto.EmbeddingResponse;
import com.dku.opensource.priorify.priorify_backend.dto.EmbeddingResult;
import com.dku.opensource.priorify.priorify_backend.dto.GoogleCalendarEventDto;
import com.dku.opensource.priorify.priorify_backend.model.Schedule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.gson.GsonFactory;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAPIService {

    private final MongoTemplate mongoTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String FAST_API_EMBEDDING_URL = "http://localhost:8000/embed";
    private static final int BATCH_SIZE = 4;
    
    // 회원가입 후, 동기화 작업
    public Single<CalendarSyncResultDto> syncGoogleCalendar(String userId, String googleAccessToken) {
        return Single.fromCallable((Callable<CalendarSyncResultDto>) () -> {
            LocalDateTime syncStartTime = LocalDateTime.now();
            CalendarSyncResultDto.CalendarSyncResultDtoBuilder resultBuilder = CalendarSyncResultDto.builder()
                    .userId(userId)
                    .syncStartTime(syncStartTime);
            
            try {
                // 1. 구글 캘린더 API 호출
                List<GoogleCalendarEventDto> events = fetchGoogleCalendarEvents(googleAccessToken);
                
                // 2. 기존 스케줄과 비교하여 처리 대상 분류
                SyncAnalysisResult analysisResult = analyzeEventsForSync(userId, events);
                
                // 3. 새로운 이벤트와 title이 변경된 이벤트만 임베딩 요청
                List<GoogleCalendarEventDto> eventsNeedingEmbedding = new ArrayList<>();
                eventsNeedingEmbedding.addAll(analysisResult.getNewEvents());
                eventsNeedingEmbedding.addAll(analysisResult.getTitleChangedEvents());

                log.info("eventsNeedingEmbedding: " + eventsNeedingEmbedding);
                List<GoogleCalendarEventDto> embeddedEvents = new ArrayList<>();
                if (!eventsNeedingEmbedding.isEmpty()) {
                    embeddedEvents = requestEmbeddings(eventsNeedingEmbedding);
                }
                System.out.println("embeddedEvents: " + embeddedEvents);
                
                // 4. MongoDB에 저장/업데이트
                SyncOperationResult operationResult = saveOrUpdateSchedules(userId, analysisResult, embeddedEvents);
                
                // 5. 결과 구성
                int totalProcessed = analysisResult.getTotalEvents();
                int successful = operationResult.getCreated() + operationResult.getUpdated();
                
                return resultBuilder
                        .syncEndTime(LocalDateTime.now())
                        .totalEventsProcessed(totalProcessed)
                        .successfulEmbeddings(embeddedEvents.size())
                        .failedEmbeddings(eventsNeedingEmbedding.size() - embeddedEvents.size())
                        .status(successful == totalProcessed ? "SUCCESS" : "PARTIAL_SUCCESS")
                        .message(String.format("동기화 완료: 신규 %d개, 업데이트 %d개, 스킵 %d개", 
                                operationResult.getCreated(), operationResult.getUpdated(), operationResult.getSkipped()))
                        .build();
                        
            } catch (Exception e) {
                log.error("캘린더 동기화 실패", e);
                return resultBuilder
                        .syncEndTime(LocalDateTime.now())
                        .status("FAILED")
                        .message("캘린더 동기화 실패: " + e.getMessage())
                        .build();
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.single());
    }
    
    // 기존 스케줄과 비교하여 동기화 전략 분석
    private SyncAnalysisResult analyzeEventsForSync(String userId, List<GoogleCalendarEventDto> events) {
        ObjectId userObjectId = new ObjectId(userId);
        
        // 기존 스케줄들을 googleEventId로 조회
        Query query = new Query(Criteria.where("userId").is(userObjectId));
        List<Schedule> existingSchedules = mongoTemplate.find(query, Schedule.class);
        
        Map<String, Schedule> existingScheduleMap = existingSchedules.stream()
                .filter(schedule -> schedule.getGoogleEventId() != null)
                .collect(Collectors.toMap(Schedule::getGoogleEventId, schedule -> schedule));
        
        List<GoogleCalendarEventDto> newEvents = new ArrayList<>();
        List<GoogleCalendarEventDto> titleChangedEvents = new ArrayList<>();
        List<GoogleCalendarEventDto> unchangedEvents = new ArrayList<>();
        
        for (GoogleCalendarEventDto event : events) {
            Schedule existingSchedule = existingScheduleMap.get(event.getGoogleEventId());
            
            if (existingSchedule == null) {
                // 새로운 이벤트
                newEvents.add(event);
            } else if (!isTitleSame(existingSchedule.getTitle(), event.getTitle())) {
                // 임베딩 재생성 필요
                titleChangedEvents.add(event);
            } else {
                // title 은 같지만, 다른 필드가 변경되었을 수 있는 이벤트
                event.setEmbedding(existingSchedule.getEmbedding());
                unchangedEvents.add(event);
            }
        }
        
        log.info("동기화 분석 결과 - 신규: {}개, title 변경: {}개, 기타 업데이트: {}개", 
                newEvents.size(), titleChangedEvents.size(), unchangedEvents.size());
        
        return SyncAnalysisResult.builder()
                .newEvents(newEvents)
                .titleChangedEvents(titleChangedEvents)
                .unchangedEvents(unchangedEvents)
                .existingScheduleMap(existingScheduleMap)
                .build();
    }
    
    // title 비교 (null-safe)
    private boolean isTitleSame(String existingTitle, String newTitle) {
        if (existingTitle == null && newTitle == null) return true;
        if (existingTitle == null || newTitle == null) return false;
        return existingTitle.trim().equals(newTitle.trim());
    }
    
    // 저장/업데이트 처리
    private SyncOperationResult saveOrUpdateSchedules(String userId, SyncAnalysisResult analysisResult, 
                                                     List<GoogleCalendarEventDto> embeddedEvents) {
        ObjectId userObjectId = new ObjectId(userId);
        int created = 0, updated = 0, skipped = 0;
        
        // 임베딩된 이벤트들을 Map으로 변환 (빠른 조회를 위해)
        Map<String, GoogleCalendarEventDto> embeddedEventMap = embeddedEvents.stream()
                .collect(Collectors.toMap(GoogleCalendarEventDto::getGoogleEventId, event -> event));
        
        // 1. 새로운 이벤트 생성
        for (GoogleCalendarEventDto event : analysisResult.getNewEvents()) {
            GoogleCalendarEventDto embeddedEvent = embeddedEventMap.get(event.getGoogleEventId());
            if (embeddedEvent != null) {
                try {
                    Schedule schedule = buildScheduleFromEvent(userObjectId, embeddedEvent);
                    mongoTemplate.save(schedule);
                    created++;
                } catch (Exception e) {
                    log.error("새 일정 저장 실패 (ID: {}): {}", event.getId(), e.getMessage());
                    skipped++;
                }
            } else {
                skipped++;
            }
        }
        
        // 2. title이 변경된 이벤트 업데이트
        for (GoogleCalendarEventDto event : analysisResult.getTitleChangedEvents()) {
            GoogleCalendarEventDto embeddedEvent = embeddedEventMap.get(event.getGoogleEventId());
            if (embeddedEvent != null) {
                try {
                    updateExistingSchedule(userObjectId, embeddedEvent, analysisResult.getExistingScheduleMap());
                    updated++;
                } catch (Exception e) {
                    log.error("일정 업데이트 실패 (ID: {}): {}", event.getId(), e.getMessage());
                    skipped++;
                }
            } else {
                skipped++;
            }
        }
        
        // 3. title은 동일하지만 다른 필드가 변경된 이벤트 업데이트
        for (GoogleCalendarEventDto event : analysisResult.getUnchangedEvents()) {
            try {
                updateExistingSchedule(userObjectId, event, analysisResult.getExistingScheduleMap());
                updated++;
            } catch (Exception e) {
                log.error("일정 업데이트 실패 (ID: {}): {}", event.getId(), e.getMessage());
                skipped++;
            }
        }
        
        log.info("동기화 완료 - 생성: {}개, 업데이트: {}개, 스킵: {}개", created, updated, skipped);
        
        return SyncOperationResult.builder()
                .created(created)
                .updated(updated)
                .skipped(skipped)
                .build();
    }
    
    // Schedule 객체 생성
    private Schedule buildScheduleFromEvent(ObjectId userObjectId, GoogleCalendarEventDto event) {
        return Schedule.builder()
                .userId(userObjectId)
                .title(event.getTitle())
                .googleEventId(event.getGoogleEventId())
                .embedding(event.getEmbedding())
                .categories(event.getCategories())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .status("active")
                .build();
    }
    
    // 기존 Schedule 업데이트
    private void updateExistingSchedule(ObjectId userObjectId, GoogleCalendarEventDto event, 
                                       Map<String, Schedule> existingScheduleMap) {
        Schedule existingSchedule = existingScheduleMap.get(event.getGoogleEventId());
        if (existingSchedule == null) {
            throw new RuntimeException("기존 스케줄을 찾을 수 없습니다: " + event.getGoogleEventId());
        }
        
        Query query = new Query(Criteria.where("_id").is(existingSchedule.getId()));
        Update update = new Update()
                .set("title", event.getTitle())
                .set("categories", event.getCategories())
                .set("startAt", event.getStartAt())
                .set("endAt", event.getEndAt())
                .set("updatedAt", LocalDateTime.now());
        
        // 임베딩이 있는 경우에만 업데이트
        if (event.getEmbedding() != null && !event.getEmbedding().isEmpty()) {
            update.set("embedding", event.getEmbedding());
        }
        
        mongoTemplate.updateFirst(query, update, Schedule.class);
    }
    
    // 구글 캘린더 이벤트 가져오기
    private List<GoogleCalendarEventDto> fetchGoogleCalendarEvents(String googleAccessToken) throws JsonProcessingException {
        // 최근 한달간의 캘린더만 가져오기 위한 파라미터 설정
        String timeMin = LocalDateTime.now().minusMonths(1)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT);
        
        String timeMax = LocalDateTime.now().plusMonths(1)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT);
        
        String googleCalendarUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                "?timeMin=" + timeMin + "&timeMax=" + timeMax +
                "&orderBy=updated&singleEvents=true";  // 업데이트 순서로 정렬, 반복 이벤트 확장
        
        // 구글 캘린더 API 호출
        HttpHeaders calendarHeaders = new HttpHeaders();
        calendarHeaders.set("Authorization", "Bearer " + googleAccessToken);
        calendarHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        ResponseEntity<String> calendarResponse = restTemplate.exchange(
                googleCalendarUrl,
                HttpMethod.GET,
                new HttpEntity<>(null, calendarHeaders),
                String.class
        );
        
        return parseCalendarEvents(calendarResponse.getBody());
    }
    
    // 구글 캘린더 이벤트 파싱
    private List<GoogleCalendarEventDto> parseCalendarEvents(String calendarData) throws JsonProcessingException {
        List<GoogleCalendarEventDto> eventList = new ArrayList<>();
        JsonNode rootNode = objectMapper.readTree(calendarData);
        JsonNode items = rootNode.path("items");
        
        if (items.isArray()) {
            for (JsonNode item : items) {
                String eventId = item.path("id").asText();
                String summary = item.path("summary").asText("");
                
                // 시작/종료 시간 파싱
                LocalDateTime startAt = parseDateTime(item.path("start"));
                LocalDateTime endAt = parseDateTime(item.path("end"));
                
                GoogleCalendarEventDto event = GoogleCalendarEventDto.builder()
                        .id(eventId)
                        .googleEventId(eventId)
                        .title(summary)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build();
                
                eventList.add(event);
            }
        }
        
        return eventList;
    }
    
    // 날짜시간 파싱 헬퍼 메서드
    private LocalDateTime parseDateTime(JsonNode dateTimeNode) {
        if (dateTimeNode.has("dateTime")) {
            return LocalDateTime.parse(dateTimeNode.path("dateTime").asText(), 
                                     DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return null;
    }
    
    // FastAPI로 임베딩 요청
    private List<GoogleCalendarEventDto> requestEmbeddings(List<GoogleCalendarEventDto> events) {
        List<GoogleCalendarEventDto> result = new ArrayList<>();
        
        // 4개씩 배치로 나누어서 처리
        for (int i = 0; i < events.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, events.size());
            List<GoogleCalendarEventDto> batch = events.subList(i, endIndex);
            System.out.println("batch: " + batch);
            try {
                List<GoogleCalendarEventDto> embeddedBatch = processBatch(batch);
                result.addAll(embeddedBatch);
            } catch (Exception e) {
                log.error("배치 처리 실패 ({}/{}): {}", i, endIndex-1, e.getMessage());
                result.addAll(batch);
            }
        }
        
        return result;
    }
    
    // 배치 처리
    private List<GoogleCalendarEventDto> processBatch(List<GoogleCalendarEventDto> batch) throws Exception {
        // 요청 본문 구성
        List<String> texts = batch.stream()
                .map(GoogleCalendarEventDto::getTitle)
                .map(title -> title != null ? title : "")
                .collect(Collectors.toList());
        
        EmbeddingRequest request = EmbeddingRequest.builder()
                .texts(texts)
                .build();
        
        // FastAPI 호출
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        HttpEntity<EmbeddingRequest> requestEntity = new HttpEntity<>(request, headers);
        
        ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                FAST_API_EMBEDDING_URL,
                HttpMethod.POST,
                requestEntity,
                EmbeddingResponse.class
        );
        // 응답 처리
        EmbeddingResponse embeddingResponse = response.getBody();
        if (embeddingResponse == null || embeddingResponse.getResults() == null) {
            throw new RuntimeException("임베딩 응답이 비어있습니다");
        }
        
        List<EmbeddingResult> results = embeddingResponse.getResults();
        List<GoogleCalendarEventDto> embeddedEvents = new ArrayList<>();
        
        for (int i = 0; i < batch.size() && i < results.size(); i++) {
            GoogleCalendarEventDto event = batch.get(i);
            EmbeddingResult embeddingResult = results.get(i);
            
            // 임베딩 결과를 이벤트에 적용
            GoogleCalendarEventDto embeddedEvent = GoogleCalendarEventDto.builder()
                    .id(event.getId())
                    .googleEventId(event.getGoogleEventId())
                    .title(event.getTitle())
                    .startAt(event.getStartAt())
                    .endAt(event.getEndAt())
                    .embedding(embeddingResult.getEmbedding())
                    .categories(embeddingResult.getCategories())
                    .originalText(embeddingResult.getOriginalText())
                    .build();
            
            embeddedEvents.add(embeddedEvent);
        }
        
        return embeddedEvents;
    }
    
    // 동기화 분석 결과 내부 클래스
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SyncAnalysisResult {
        private List<GoogleCalendarEventDto> newEvents;
        private List<GoogleCalendarEventDto> titleChangedEvents;
        private List<GoogleCalendarEventDto> unchangedEvents;
        private Map<String, Schedule> existingScheduleMap;
        
        public int getTotalEvents() {
            return newEvents.size() + titleChangedEvents.size() + unchangedEvents.size();
        }
    }
    
    // 동기화 작업 결과 내부 클래스
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SyncOperationResult {
        private int created;
        private int updated;
        private int skipped;
    }
}
