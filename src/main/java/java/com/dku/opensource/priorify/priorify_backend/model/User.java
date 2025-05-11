package java.com.dku.opensource.priorify.priorify_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;
import java.util.List;

@Document(collection = "users")
public class User {
    
    @Id
    private ObjectId id;
    
    @Indexed(unique = true)
    private String name;
    private String password;
    private List<CategoryPriority> highPriorities;
    private List<CategoryPriority> lowPriorities;
    
    // 기본 생성자
    public User() {}
    
    // 생성자
    public User(String name, String password) {
        this.name = name;
        this.password = password;
    }
    
    // Getters and Setters
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    

    public List<CategoryPriority> getHighPriorities() {
        return highPriorities;
    }

    public void setHighPriorities(List<CategoryPriority> highPriorities) {
        this.highPriorities = highPriorities;
    }

    public List<CategoryPriority> getLowPriorities() {
        return lowPriorities;
    }

    public void setLowPriorities(List<CategoryPriority> lowPriorities) {
        this.lowPriorities = lowPriorities;
    }
} 