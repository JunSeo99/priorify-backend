package com.dku.opensource.priorify.priorify_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import org.bson.types.ObjectId;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "users")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    
    @Id
    private ObjectId id;
    
    private String name;
    private String googleId;
    private String email;
    
    private List<CategoryPriority> highPriorities;
    private List<CategoryPriority> lowPriorities;

    public List<CategoryPriority> getHighPriorities() {
        return highPriorities != null ? highPriorities : new ArrayList<>();
    }

    public List<CategoryPriority> getLowPriorities() {
        return lowPriorities != null ? lowPriorities : new ArrayList<>();
    }
} 