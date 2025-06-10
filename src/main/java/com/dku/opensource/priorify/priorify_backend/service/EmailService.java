package com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.dto.ScheduleListDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmailService {
    private final JavaMailSender javaMailSender;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm");

    @Async
    public void sendEmailNotice(String email, String subject, List<ScheduleListDto> schedules, int daysRemaining) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mimeMessageHelper.setTo(email);
            mimeMessageHelper.setSubject(subject);

            String htmlContent = generateScheduleEmailHtml(schedules, daysRemaining);
            mimeMessageHelper.setText(htmlContent, true); // HTML 메일로 설정

            javaMailSender.send(mimeMessage);
            log.info("Succeeded to send HTML Email to {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send HTML Email to {}: {}", email, e.getMessage());
            throw e; // 예외를 다시 던져 호출 측에서 처리하도록 함
        } catch (Exception e) {
            log.error("An unexpected error occurred while sending email to {}: {}", email, e.getMessage());
            // 개발 중에는 RuntimeException으로 처리할 수 있지만, 실제 운영에서는 더 구체적인 예외 처리 권장
            throw new RuntimeException("Email sending failed due to an unexpected error", e);
        }
    }

    @Async
    public void sendTopPriorityScheduleNotice(String email, String userName, List<ScheduleListDto> topSchedules) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mimeMessageHelper.setTo(email);
            mimeMessageHelper.setSubject("🔥 Priorify: 오늘의 최우선 처리 업무!");

            String htmlContent = generateTopPriorityEmailHtml(userName, topSchedules);
            mimeMessageHelper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("Succeeded to send Top Priority HTML Email to {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send Top Priority HTML Email to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred while sending top priority email to {}: {}", email, e.getMessage());
            throw new RuntimeException("Top priority email sending failed due to an unexpected error", e);
        }
    }

    private String generateScheduleEmailHtml(List<ScheduleListDto> schedules, int daysRemaining) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html><head><style>");
        htmlBuilder.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f9f9f9; color: #333; }");
        htmlBuilder.append("h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }");
        htmlBuilder.append(".container { background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        htmlBuilder.append("ul { list-style-type: none; padding: 0; }");
        htmlBuilder.append("li { background-color: #ecf0f1; margin-bottom: 10px; padding: 15px; border-radius: 5px; border-left: 5px solid #3498db; }");
        htmlBuilder.append("li.high-priority { border-left-color: #e74c3c; background-color: #fdedec; }"); // 높은 우선순위 스타일
        htmlBuilder.append("li.medium-priority { border-left-color: #f39c12; background-color: #fef5e7; }"); // 중간 우선순위 스타일
        htmlBuilder.append("strong { color: #3498db; }");
        htmlBuilder.append("li.high-priority strong { color: #e74c3c; }");
        htmlBuilder.append("li.medium-priority strong { color: #f39c12; }");
        htmlBuilder.append(".schedule-title { font-size: 1.1em; font-weight: bold; margin-bottom: 5px; }");
        htmlBuilder.append(".schedule-meta { font-size: 0.9em; color: #7f8c8d; }");
        htmlBuilder.append(".footer { margin-top: 20px; text-align: center; font-size: 0.8em; color: #95a5a6; }");
        htmlBuilder.append("</style></head><body>");
        htmlBuilder.append("<div class='container'>");

        String greeting;
        if (daysRemaining == 0) {
            greeting = "오늘 처리해야 할 중요한 작업이 있습니다!";
        } else if (daysRemaining == 1) {
            greeting = "내일 마감되는 주요 스케줄을 확인하세요!";
        } else {
            greeting = String.format("%d일 후 시작되거나 마감되는 스케줄 알림입니다.", daysRemaining);
        }
        htmlBuilder.append("<h1>").append(greeting).append("</h1>");

        if (schedules.isEmpty()) {
            htmlBuilder.append("<p>알림 드릴 스케줄이 없습니다.</p>");
        } else {
            htmlBuilder.append("<ul>");
            for (ScheduleListDto schedule : schedules) {
                String priorityClass = "";
                String priorityText = "보통";
                if (schedule.getPriority() != null) {
                    if (schedule.getPriority() >= 5.0) { // 예: 가중치 5.0 이상은 매우 중요
                        priorityClass = "high-priority";
                        priorityText = "매우 중요";
                    } else if (schedule.getPriority() >= 3.0) { // 예: 가중치 3.0 이상은 중요
                        priorityClass = "medium-priority";
                        priorityText = "중요";
                    }
                }

                htmlBuilder.append("<li class='").append(priorityClass).append("'>");
                htmlBuilder.append("<div class='schedule-title'>").append(schedule.getTitle() != null ? schedule.getTitle() : "제목 없음").append("</div>");
                htmlBuilder.append("<div class='schedule-meta'>");
                if (schedule.getStartDate() != null) {
                    htmlBuilder.append("시작: <strong>").append(schedule.getStartDate().format(DATE_FORMATTER)).append("</strong>");
                }
                if (schedule.getEndDate() != null) {
                    htmlBuilder.append(" (마감: <strong>").append(schedule.getEndDate().format(DATE_FORMATTER)).append("</strong>)");
                }
                htmlBuilder.append("<br>상태: ").append(schedule.getStatus());
                htmlBuilder.append(" | 중요도: <strong>").append(priorityText).append(String.format(" (%.2f)", schedule.getPriority() != null ? schedule.getPriority() : 0.0)).append("</strong>");
                htmlBuilder.append("</div>");
                htmlBuilder.append("</li>");
            }
            htmlBuilder.append("</ul>");
        }
        htmlBuilder.append("<div class='footer'><p>&copy; Priorify - 당신의 우선순위를 관리하세요.</p></div>");
        htmlBuilder.append("</div></body></html>");
        return htmlBuilder.toString();
    }

    private String generateTopPriorityEmailHtml(String userName, List<ScheduleListDto> topSchedules) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html><html><head>");
        htmlBuilder.append("<meta charset='UTF-8'>");
        htmlBuilder.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        htmlBuilder.append("<style>");
        
        // 개선된 CSS 스타일링 - 가독성 향상
        htmlBuilder.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #333; }");
        htmlBuilder.append(".email-container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 15px; overflow: hidden; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }");
        htmlBuilder.append(".header { background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%); padding: 30px; text-align: center; color: white; }");
        htmlBuilder.append(".header h1 { margin: 0; font-size: 28px; font-weight: 700; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); color: #ffffff; }");
        htmlBuilder.append(".header .emoji { font-size: 40px; margin-bottom: 10px; display: block; }");
        htmlBuilder.append(".greeting { padding: 25px 30px 15px; font-size: 18px; color: #2c3e50; background: #ffffff; }");
        htmlBuilder.append(".schedule-card { margin: 20px 30px; padding: 25px; background: #ffffff; border-radius: 12px; border-left: 6px solid #e74c3c; box-shadow: 0 8px 16px rgba(0,0,0,0.1); position: relative; overflow: hidden; border: 1px solid #e9ecef; }");
        htmlBuilder.append(".schedule-card:nth-child(even) { border-left-color: #3498db; }");
        htmlBuilder.append(".schedule-card::before { content: ''; position: absolute; top: 0; right: 0; width: 100px; height: 100px; background: radial-gradient(circle, rgba(52, 152, 219, 0.1) 0%, transparent 70%); border-radius: 50%; transform: translate(50%, -50%); }");
        htmlBuilder.append(".schedule-rank { position: absolute; top: 15px; right: 20px; background: #e74c3c; color: #ffffff; border-radius: 20px; padding: 5px 12px; font-size: 12px; font-weight: bold; z-index: 10; }");
        htmlBuilder.append(".schedule-card:nth-child(even) .schedule-rank { background: #3498db; color: #ffffff; }");
        htmlBuilder.append(".schedule-title { font-size: 22px; font-weight: 700; margin-bottom: 15px; color: #2c3e50; line-height: 1.3; z-index: 5; position: relative; }");
        htmlBuilder.append(".schedule-meta { display: flex; flex-wrap: wrap; gap: 15px; margin-bottom: 15px; z-index: 5; position: relative; }");
        htmlBuilder.append(".meta-item { display: flex; align-items: center; font-size: 14px; color: #5a6c7d; font-weight: 500; }");
        htmlBuilder.append(".meta-item strong { color: #34495e; font-weight: 700; }");
        htmlBuilder.append(".meta-icon { width: 16px; height: 16px; margin-right: 6px; }");
        htmlBuilder.append(".priority-badge { display: inline-block; padding: 8px 16px; border-radius: 25px; font-size: 14px; font-weight: bold; text-transform: uppercase; letter-spacing: 0.5px; z-index: 5; position: relative; }");
        htmlBuilder.append(".priority-high { background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%); color: #ffffff; box-shadow: 0 4px 8px rgba(231, 76, 60, 0.3); text-shadow: 1px 1px 2px rgba(0,0,0,0.2); }");
        htmlBuilder.append(".priority-medium { background: linear-gradient(135deg, #f39c12 0%, #e67e22 100%); color: #ffffff; box-shadow: 0 4px 8px rgba(243, 156, 18, 0.3); text-shadow: 1px 1px 2px rgba(0,0,0,0.2); }");
        htmlBuilder.append(".cta-section { padding: 30px; text-align: center; background: #f8f9fa; }");
        htmlBuilder.append(".cta-section p { color: #6c757d; font-weight: 500; }");
        htmlBuilder.append(".cta-button { display: inline-block; padding: 15px 30px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff; text-decoration: none; border-radius: 25px; font-weight: bold; font-size: 16px; box-shadow: 0 8px 16px rgba(102, 126, 234, 0.3); transition: transform 0.2s ease; text-shadow: 1px 1px 2px rgba(0,0,0,0.2); }");
        htmlBuilder.append(".cta-button:hover { transform: translateY(-2px); }");
        htmlBuilder.append(".footer { padding: 20px 30px; background: #2c3e50; color: #bdc3c7; text-align: center; font-size: 12px; }");
        htmlBuilder.append(".footer a { color: #3498db; text-decoration: none; font-weight: 500; }");
        htmlBuilder.append(".footer a:hover { color: #5dade2; }");
        
        htmlBuilder.append("</style></head><body>");
        
        htmlBuilder.append("<div class='email-container'>");
        
        // 헤더
        htmlBuilder.append("<div class='header'>");
        htmlBuilder.append("<span class='emoji'>🔥</span>");
        htmlBuilder.append("<h1>최우선 업무 알림</h1>");
        htmlBuilder.append("</div>");
        
        // 인사말
        htmlBuilder.append("<div class='greeting'>");
        htmlBuilder.append("안녕하세요, <strong>").append(userName).append("</strong>님!<br>");
        htmlBuilder.append("오늘 처리해야 할 <strong>가장 중요한 업무</strong>를 알려드립니다.");
        htmlBuilder.append("</div>");
        
        // 스케줄 카드들
        for (int i = 0; i < topSchedules.size(); i++) {
            ScheduleListDto schedule = topSchedules.get(i);
            htmlBuilder.append("<div class='schedule-card'>");
            htmlBuilder.append("<div class='schedule-rank'>").append(i == 0 ? "1순위" : "2순위").append("</div>");
            
            htmlBuilder.append("<div class='schedule-title'>").append(schedule.getTitle() != null ? schedule.getTitle() : "제목 없음").append("</div>");
            
            htmlBuilder.append("<div class='schedule-meta'>");
            if (schedule.getStartDate() != null) {
                htmlBuilder.append("<div class='meta-item'>");
                htmlBuilder.append("📅 시작: <strong>").append(schedule.getStartDate().format(DATE_FORMATTER)).append("</strong>");
                htmlBuilder.append("</div>");
            }
            if (schedule.getEndDate() != null) {
                htmlBuilder.append("<div class='meta-item'>");
                htmlBuilder.append("⏰ 마감: <strong>").append(schedule.getEndDate().format(DATE_FORMATTER)).append("</strong>");
                htmlBuilder.append("</div>");
            }
            htmlBuilder.append("</div>");
            
            // 우선순위 배지
            String priorityClass = "priority-medium";
            String priorityText = "중요";
            if (schedule.getPriority() != null && schedule.getPriority() >= 7.0) {
                priorityClass = "priority-high";
                priorityText = "매우 중요";
            }
            
            htmlBuilder.append("<div class='priority-badge ").append(priorityClass).append("'>");
            htmlBuilder.append("우선순위: ").append(priorityText).append(" (").append(String.format("%.2f", schedule.getPriority() != null ? schedule.getPriority() : 0.0)).append(")");
            htmlBuilder.append("</div>");
            
            htmlBuilder.append("</div>");
        }
        
        // CTA 섹션
        htmlBuilder.append("<div class='cta-section'>");
        htmlBuilder.append("<p style='margin-bottom: 20px; font-size: 16px; color: #6c757d; font-weight: 500;'>지금 바로 시작해서 생산적인 하루를 만들어보세요!</p>");
        htmlBuilder.append("<a href='https://priorify-one.vercel.app/schedule' class='cta-button'>Priorify에서 확인하기</a>");
        htmlBuilder.append("</div>");
        
        // 푸터
        htmlBuilder.append("<div class='footer'>");
        htmlBuilder.append("<p>&copy; 2024 Priorify - 스마트한 우선순위 관리<br>");
        htmlBuilder.append("</div>");
        
        htmlBuilder.append("</div></body></html>");
        
        return htmlBuilder.toString();
    }

    public String todayDate(){
        LocalDateTime todayDate = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M월 d일");
        return todayDate.format(formatter);
    }
}