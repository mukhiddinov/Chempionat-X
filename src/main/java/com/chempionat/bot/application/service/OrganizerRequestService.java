package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.OrganizerRequest;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.OrganizerRequestRepository;
import com.chempionat.bot.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizerRequestService {

    private final OrganizerRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Transactional
    public OrganizerRequest submitRequest(User user) {
        // Check if user already has organizer/admin role
        if (user.getRole() == Role.ORGANIZER || user.getRole() == Role.ADMIN) {
            throw new IllegalStateException("Siz allaqachon tashkilotchi yoki admin roliga egasiz");
        }

        // Check if there's already a pending request
        Optional<OrganizerRequest> existingRequest = requestRepository.findByUserAndStatus(user, "PENDING");
        if (existingRequest.isPresent()) {
            throw new IllegalStateException("Sizning so'rovingiz allaqachon ko'rib chiqilmoqda");
        }

        OrganizerRequest request = OrganizerRequest.builder()
                .user(user)
                .status("PENDING")
                .build();

        OrganizerRequest saved = requestRepository.save(request);
        log.info("Organizer request submitted by user: {}", user.getTelegramId());
        
        // AUTOMATIC NOTIFICATION TO ALL ADMINS
        notifyAdminsAboutRequest(saved);
        
        return saved;
    }

    @Transactional
    public void approveRequest(Long requestId, User admin) {
        OrganizerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Bu so'rov allaqachon ko'rib chiqilgan");
        }

        // Grant ORGANIZER role to user
        User user = request.getUser();
        user.setRole(Role.ORGANIZER);
        userRepository.save(user);

        // Update request
        request.setStatus("APPROVED");
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        requestRepository.save(request);

        log.info("Organizer request {} approved by admin {}", requestId, admin.getTelegramId());
        
        // Notify user about approval
        notificationService.notifyUser(user.getTelegramId(),
                "✅ Sizning tashkilotchi bo'lish so'rovingiz qabul qilindi!\n\n" +
                "Endi siz turnirlar yaratishingiz mumkin: /createtournament");
    }

    @Transactional
    public void rejectRequest(Long requestId, User admin, String comment) {
        OrganizerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Bu so'rov allaqachon ko'rib chiqilgan");
        }

        request.setStatus("REJECTED");
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewComment(comment);
        requestRepository.save(request);

        log.info("Organizer request {} rejected by admin {}", requestId, admin.getTelegramId());
        
        // Notify user about rejection
        User user = request.getUser();
        notificationService.notifyUser(user.getTelegramId(),
                "❌ Sizning tashkilotchi bo'lish so'rovingiz rad etildi.\n\n" +
                "Sabab: " + comment);
    }

    @Transactional(readOnly = true)
    public List<OrganizerRequest> getPendingRequests() {
        return requestRepository.findByStatus("PENDING");
    }
    
    /**
     * Send automatic notification to all admins about new organizer request
     */
    private void notifyAdminsAboutRequest(OrganizerRequest request) {
        User requestUser = request.getUser();
        
        StringBuilder message = new StringBuilder();
        message.append("🔔 Yangi tashkilotchi so'rovi!\n\n");
        message.append(String.format("👤 Foydalanuvchi: %s\n",
                requestUser.getUsername() != null ? 
                "@" + requestUser.getUsername() : 
                requestUser.getFirstName()));
        message.append(String.format("🆔 Telegram ID: %d\n", requestUser.getTelegramId()));
        message.append(String.format("📅 So'rov vaqti: %s\n",
                request.getCreatedAt().format(DATE_FORMATTER)));
        
        // Create inline keyboard
        InlineKeyboardMarkup keyboard = createApprovalKeyboard(request.getId());
        
        // Send to all admins
        notificationService.notifyAdminsWithKeyboard(message.toString(), keyboard);
        
        log.info("Notification sent to admins about organizer request: {}", request.getId());
    }
    
    private InlineKeyboardMarkup createApprovalKeyboard(Long requestId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton approveButton = InlineKeyboardButton.builder()
                .text("✅ Qabul qilish")
                .callbackData("approveorganizer:" + requestId)
                .build();
        
        InlineKeyboardButton rejectButton = InlineKeyboardButton.builder()
                .text("❌ Rad etish")
                .callbackData("rejectorganizer:" + requestId)
                .build();

        row.add(approveButton);
        row.add(rejectButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }
}

