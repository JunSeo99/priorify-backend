package java.com.dku.opensource.priorify.priorify_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "schedules")
public class Schedule {
    
    @Id
    private ObjectId id;
    
    @Indexed
    @DBRef
    private User user;
    
    private String title;
    private String description;
    
    @Indexed
    private LocalDateTime datetime;
    
    private String category;
    private List<String> relatedCategories;
    
    @Indexed
    private Double priorityScore;
    
    private LocalDateTime createdAt;
    private boolean isCompleted;
    private double importance;

    // 기본 생성자
    public Schedule() {
        this.createdAt = LocalDateTime.now();
        this.isCompleted = false;
    }

    // 모든 필드를 포함하는 생성자
    public Schedule(User user, String title, String description, 
                   LocalDateTime datetime, String category, Double priorityScore) {
        this();
        this.user = user;
        this.title = title;
        this.description = description;
        this.datetime = datetime;
        this.category = category;
        this.priorityScore = priorityScore;
    }

    // Getters and Setters
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDatetime() {
        return datetime;
    }

    public void setDatetime(LocalDateTime datetime) {
        this.datetime = datetime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getRelatedCategories() {
        return relatedCategories;
    }

    public void setRelatedCategories(List<String> relatedCategories) {
        this.relatedCategories = relatedCategories;
    }

    public Double getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(Double priorityScore) {
        this.priorityScore = priorityScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }
} 