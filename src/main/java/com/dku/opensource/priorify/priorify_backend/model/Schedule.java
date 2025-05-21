package com.dku.opensource.priorify.priorify_backend.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.mongodb.core.mapping.DBRef;
import org.bson.types.ObjectId;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "schedules")
@CompoundIndexes({
  @CompoundIndex(name = "user_start", def = "{ 'userId': 1, 'startAt': 1 }")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;
    private String category; // 일정 카테고리 (학교, 취업, 취미, 친목, 등등)

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private String status; // 일정 상태 (active, completed)
    private List<ObjectId> connections; // 인접 일정 id

    private String source;// google, priorify
    private String externalEventId; // google event id

    private List<Double> priorityVector;   // 우선순위 벡터 임베딩 (endAt, startAt, users collection의 highPriorities, lowPriorities을 고려)

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

}