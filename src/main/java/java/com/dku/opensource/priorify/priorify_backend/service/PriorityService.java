package java.com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;
import com.dku.opensource.priorify.priorify_backend.model.User;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.Optional;

@Service
public class PriorityService {
    
    private final UserService userService;

    public PriorityService(UserService userService) {
        this.userService = userService;
    }

    public void setHighPriorities(ObjectId userId, List<CategoryPriority> priorities) {
        Optional<User> userOpt = userService.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        User user = userOpt.get();
        user.setHighPriorities(priorities);
        userService.save(user);
    }

    public void setLowPriorities(ObjectId userId, List<CategoryPriority> priorities) {
        Optional<User> userOpt = userService.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        User user = userOpt.get();
        user.setLowPriorities(priorities);
        userService.save(user);
    }

    public List<CategoryPriority> getHighPriorities(ObjectId userId) {
        Optional<User> userOpt = userService.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        return userOpt.get().getHighPriorities();
    }

    public List<CategoryPriority> getLowPriorities(ObjectId userId) {
        Optional<User> userOpt = userService.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        return userOpt.get().getLowPriorities();
    }

    public double calculatePriorityScore(String category, String userName) {
        User user = userService.findByName(userName);
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        
        List<CategoryPriority> highPriorities = user.getHighPriorities();
        List<CategoryPriority> lowPriorities = user.getLowPriorities();

        // 상위 우선순위에서 검색
        for (CategoryPriority priority : highPriorities) {
            if (priority.getCategory().equals(category)) {
                return calculateScore(priority.getRank(), true);
            }
        }

        // 하위 우선순위에서 검색
        for (CategoryPriority priority : lowPriorities) {
            if (priority.getCategory().equals(category)) {
                return calculateScore(priority.getRank(), false);
            }
        }

        // 우선순위가 설정되지 않은 경우 기본값 반환
        return 0.5;
    }

    private double calculateScore(int rank, boolean isHigh) {
        if (isHigh) {
            // 상위 우선순위: 0.7 ~ 0.9
            return 0.9 - ((rank - 1) * 0.1);
        } else {
            // 하위 우선순위: 0.1 ~ 0.3
            return 0.3 - ((rank - 1) * 0.1);
        }
    }
} 