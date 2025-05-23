package com.dku.opensource.priorify.priorify_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

import org.bson.Document;
import org.bson.types.ObjectId;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAPIService {

    private final MongoTemplate mongoTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // FastAPI 서비스 URL (같은 VPC 내부에 있다고 가정)
    private static final String FAST_API_EMBEDDING_URL = "http://fastapi-service/embedding";
    
    // 회원가입 후, 동기화 작업
    public Single<String> syncGoogleCalendar(String userId, String googleAccessToken) {
        return Single.fromCallable((Callable<String>) () -> {
            try {
                // 최근 한달간의 캘린더만 가져오기 위한 파라미터 설정
                String timeMin = LocalDateTime.now().minusMonths(1)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                
                String timeMax = LocalDateTime.now()
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                
                String googleCalendarUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                        "?timeMin=" + timeMin + "&timeMax=" + timeMax;
                
                // 1. 구글 캘린더 API 호출
                HttpHeaders calendarHeaders = new HttpHeaders();
                calendarHeaders.set("Authorization", "Bearer " + googleAccessToken);
                calendarHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                
                ResponseEntity<String> calendarResponse = restTemplate.exchange(
                        googleCalendarUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(null, calendarHeaders),
                        String.class
                );
                
                // 2. 캘린더 이벤트 파싱
                List<Map<String, Object>> eventList = parseCalendarEvents(calendarResponse.getBody());
                
                // 3. FastAPI로 임베딩 요청
                List<Map<String, Object>> embeddedEvents = requestEmbeddings(eventList);
                
                // 4. MongoDB에 저장
                saveToMongoDB(userId, embeddedEvents);
                
                return "캘린더 동기화 완료: " + eventList.size() + "개의 일정 처리됨";
            } catch (Exception e) {
                log.error("캘린더 동기화 실패", e);
                throw e;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.single());
    }
    
    // 구글 캘린더 이벤트 파싱
    private List<Map<String, Object>> parseCalendarEvents(String calendarData) throws JsonProcessingException {
        List<Map<String, Object>> eventList = new ArrayList<>();
        JsonNode rootNode = objectMapper.readTree(calendarData);
        JsonNode items = rootNode.path("items");
        
        if (items.isArray()) {
            for (JsonNode item : items) {
                String eventId = item.path("id").asText();
                String summary = item.path("summary").asText("");
                String description = item.path("description").asText("");
                String location = item.path("location").asText("");
                
                // 시작/종료 시간 파싱
                LocalDateTime startAt = null;
                LocalDateTime endAt = null;
                
                JsonNode start = item.path("start");
                JsonNode end = item.path("end");
                
                if (start.has("dateTime")) {
                    startAt = LocalDateTime.parse(start.path("dateTime").asText(), 
                                               DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
                
                if (end.has("dateTime")) {
                    endAt = LocalDateTime.parse(end.path("dateTime").asText(), 
                                             DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
                
                // 이벤트 정보 저장
                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("id", eventId);
                eventMap.put("title", summary);
                eventMap.put("description", description);
                eventMap.put("location", location);
                eventMap.put("startAt", startAt);
                eventMap.put("endAt", endAt);
                
                eventList.add(eventMap);
            }
        }
        
        return eventList;
    }
    
    // FastAPI로 임베딩 요청
    private List<Map<String, Object>> requestEmbeddings(List<Map<String, Object>> events) throws JsonProcessingException {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> event : events) {
            // FastAPI에 전송할 요청 본문 구성
            Map<String, Object> requestBody = new HashMap<>();
            List<String> titles = new ArrayList<>();
            titles.add((String) event.get("title"));
            
            requestBody.put("title", titles);
            requestBody.put("id", event.get("id"));
            
            // FastAPI 호출
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    FAST_API_EMBEDDING_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            
            // 응답 파싱
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            
            // 임베딩 벡터 파싱 (864차원 벡터)
            List<Double> embeddingVector = null;
            JsonNode embeddingNode = responseNode.path("embedding");
            if (embeddingNode.isArray()) {
                embeddingVector = objectMapper.convertValue(embeddingNode, new TypeReference<List<Double>>() {});
            } else {
                log.warn("임베딩 벡터 형식이 예상과 다릅니다: {}", embeddingNode);
                embeddingVector = new ArrayList<>();
            }
            
            // 결과 저장
            Map<String, Object> embeddedEvent = new HashMap<>(event);
            embeddedEvent.put("embedding", embeddingVector);
            embeddedEvent.put("id", responseNode.path("id").asText());
            
            result.add(embeddedEvent);
        }
        
        return result;
    }
    
    // MongoDB에 저장
    private void saveToMongoDB(String userId, List<Map<String, Object>> embeddedEvents) {
        ObjectId userObjectId = new ObjectId(userId);
        
        for (Map<String, Object> event : embeddedEvents) {
            @SuppressWarnings("unchecked")
            List<Double> embeddingVector = (List<Double>) event.get("embedding");
            
            Schedule schedule = Schedule.builder()
                .userId(userObjectId)
                .title((String) event.get("title"))
                .description((String) event.get("description"))
                .location((String) event.get("location"))
                .googleEventId((String) event.get("id"))
                .embedding(embeddingVector)
                .startAt((LocalDateTime) event.get("startAt"))
                .endAt((LocalDateTime) event.get("endAt"))
                .status("active")
                .build();
            
            // MongoDB에 저장
            mongoTemplate.save(schedule);
        }
    }
}
