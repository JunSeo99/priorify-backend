package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;
import com.dku.opensource.priorify.priorify_backend.model.User;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    public List<CategoryPriority> getHighPriorities(String userId) {
        Optional<User> userOpt = userService.findById(new ObjectId(userId));
        if (!userOpt.isPresent()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        return userOpt.get().getHighPriorities();
    }

    public List<CategoryPriority> getLowPriorities(String userId) {
        Optional<User> userOpt = userService.findById(new ObjectId(userId));
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

    /**
     * 일정의 우선순위 벡터를 계산합니다.
     * 우선순위 벡터는 [종합점수, 시간점수, 사용자선호도] 형태로 구성됩니다.
     * 
     * @param category 일정 카테고리
     * @param user 사용자 정보
     * @param startAt 시작 시간
     * @param endAt 종료 시간
     * @return 우선순위 벡터 (Double 리스트)
     */
    public List<Double> calculatePriorityVector(String category, User user, LocalDateTime startAt, LocalDateTime endAt) {
        List<Double> priorityVector = new ArrayList<>();
        
        // 시간 기반 점수 (마감 시간이 가까울수록 우선순위 증가)
        double timeScore = calculateTimeScore(startAt, endAt);
        
        // 사용자 우선순위 기반 점수
        double userPriorityScore = calculateUserPriorityScore(category, user);
        
        // 종합 점수
        double overallScore = (timeScore * 0.6) + (userPriorityScore * 0.4);
        
        // 벡터에 점수 추가 [종합점수, 시간점수, 사용자선호도]
        priorityVector.add(overallScore);  // 종합 점수
        priorityVector.add(timeScore);     // 시간 점수
        priorityVector.add(userPriorityScore); // 사용자 선호도 점수
        
        // 시간 벡터 추가 (서치용)
        List<Double> timeVector = createTimeVector(startAt, endAt);
        priorityVector.addAll(timeVector);
        
        return priorityVector;
    }
    
    /**
     * 시간을 벡터로 변환합니다. 이 벡터는 시간 특성을 고정된 차원의 벡터로 표현합니다.
     * 시간 벡터는 다음 차원으로 구성됩니다:
     * [
     *   요일(7차원), 
     *   시간대(24차원), 
     *   월(12차원), 
     *   계절(4차원), 
     *   주기(1: 단기, 0: 장기)
     * ]
     * 총 48차원의 벡터지만, 희소 표현(sparse representation)을 위해 활성화된 값만 저장합니다.
     * 
     * @param startAt 시작 시간
     * @param endAt 종료 시간
     * @return 시간 벡터
     */
    public List<Double> createTimeVector(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            // 시간 정보가 없는 경우 빈 벡터 반환
            return Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        
        // 시간 특성 추출
        // 1. 요일 정보 (0.0-1.0 사이로 정규화)
        double dayOfWeekValue = (double) startAt.getDayOfWeek().getValue() / 7.0;
        
        // 2. 시간대 정보 (0.0-1.0 사이로 정규화)
        double hourOfDayValue = (double) startAt.getHour() / 23.0;
        
        // 3. 월 정보 (0.0-1.0 사이로 정규화)
        double monthValue = (double) startAt.getMonthValue() / 12.0;
        
        // 4. 계절 정보 (Spring: 0.25, Summer: 0.5, Fall: 0.75, Winter: 1.0)
        double seasonValue = getSeasonValue(startAt.getMonth());
        
        // 5. 주기 특성
        // 일정 길이가 하루 미만이면 단기(1.0), 그 외는 장기(0.0)
        boolean isShortTerm = ChronoUnit.HOURS.between(startAt, endAt) < 24;
        double durationValue = isShortTerm ? 1.0 : 0.0;
        
        // 6. 현재 시점으로부터의 근접성 (0.0-1.0 사이로 정규화)
        double proximityValue = calculateProximityValue(startAt);
        
        // 시간 벡터 생성
        List<Double> timeVector = new ArrayList<>();
        timeVector.add(dayOfWeekValue);
        timeVector.add(hourOfDayValue);
        timeVector.add(monthValue);
        timeVector.add(seasonValue);
        timeVector.add(durationValue);
        timeVector.add(proximityValue);
        
        return timeVector;
    }
    
    /**
     * 계절 값을 계산합니다 (Spring: 0.25, Summer: 0.5, Fall: 0.75, Winter: 1.0)
     */
    private double getSeasonValue(Month month) {
        switch (month) {
            case MARCH:
            case APRIL:
            case MAY:
                return 0.25; // Spring
            case JUNE:
            case JULY:
            case AUGUST:
                return 0.5; // Summer
            case SEPTEMBER:
            case OCTOBER:
            case NOVEMBER:
                return 0.75; // Fall
            default:
                return 1.0; // Winter (DECEMBER, JANUARY, FEBRUARY)
        }
    }
    
    /**
     * 현재 시간으로부터의 근접성 값을 계산합니다 (0.0-1.0 사이로 정규화)
     * 현재에 가까울수록 1.0에 가깝고, 먼 미래일수록 0.0에 가까움
     */
    private double calculateProximityValue(LocalDateTime time) {
        LocalDateTime now = LocalDateTime.now();
        
        // 과거 일정은 0.0으로 처리
        if (time.isBefore(now)) {
            return 0.0;
        }
        
        // 최대 3개월(약 90일) 이내의 근접성 계산
        long daysUntil = ChronoUnit.DAYS.between(now, time);
        if (daysUntil > 90) {
            return 0.0;
        }
        
        // 선형적으로 감소하는 근접성 값 (0-90일 → 1.0-0.0)
        return 1.0 - (daysUntil / 90.0);
    }
    
    /**
     * 시간 기반 우선순위 점수를 계산합니다.
     * 마감 시간이 가까울수록 높은 점수를 반환합니다.
     */
    private double calculateTimeScore(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            return 0.5; // 기본값
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 이미 시작된 일정
        if (now.isAfter(startAt)) {
            // 마감 시간이 지났으면 낮은 점수
            if (now.isAfter(endAt)) {
                return 0.3;
            }
            
            // 마감 시간이 가까울수록 높은 점수
            long totalMinutes = ChronoUnit.MINUTES.between(startAt, endAt);
            long remainingMinutes = ChronoUnit.MINUTES.between(now, endAt);
            
            if (totalMinutes <= 0) {
                return 0.8; // 시작과 종료가 같은 경우 높은 우선순위
            }
            
            // 남은 시간 비율의 역수를 사용하여 점수 계산 (남은 시간이 적을수록 높은 점수)
            double remainingRatio = (double) remainingMinutes / totalMinutes;
            return 0.5 + (0.5 * (1 - remainingRatio));
        } 
        // 아직 시작되지 않은 일정
        else {
            // 시작 시간이 가까울수록 높은 점수
            long hoursToStart = ChronoUnit.HOURS.between(now, startAt);
            
            if (hoursToStart <= 24) {
                return 0.7; // 24시간 이내에 시작하는 일정
            } else if (hoursToStart <= 72) {
                return 0.5; // 3일 이내에 시작하는 일정
            } else {
                return 0.3; // 3일 이후에 시작하는 일정
            }
        }
    }
    
    /**
     * 사용자 우선순위 기반 점수를 계산합니다.
     * 사용자 우선순위와 카테고리 순위를 기반으로 점수를 계산합니다.
     */
    private double calculateUserPriorityScore(String category, User user) {
        if (category == null || user == null) {
            return 0.5; // 기본값
        }
        
        List<CategoryPriority> highPriorities = user.getHighPriorities();
        List<CategoryPriority> lowPriorities = user.getLowPriorities();
        
        if (highPriorities != null) {
            // 상위 우선순위 카테고리의 순위 기반 점수 계산
            for (CategoryPriority priority : highPriorities) {
                if (priority.getCategory().equals(category)) {
                    // 순위 기반 점수 계산 (1순위: 0.9, 2순위: 0.8, ...)
                    int rank = priority.getRank();
                    return 0.9 - ((rank - 1) * 0.1);
                }
            }
        }
        
        if (lowPriorities != null) {
            // 하위 우선순위 카테고리의 순위 기반 점수 계산
            for (CategoryPriority priority : lowPriorities) {
                if (priority.getCategory().equals(category)) {
                    // 순위 기반 점수 계산 (1순위: 0.3, 2순위: 0.2, ...)
                    int rank = priority.getRank();
                    return 0.3 - ((rank - 1) * 0.1);
                }
            }
        }
        
        return 0.5; // 기본 우선순위
    }
} 