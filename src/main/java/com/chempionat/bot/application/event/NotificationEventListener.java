package com.chempionat.bot.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventListener {

    @Async
    @EventListener
    public void handleUserStartedEvent(UserStartedEvent event) {
        log.info("User started notification: userId={}, username={}", 
            event.getUser().getId(), 
            event.getUser().getUsername());
        
        // Placeholder for future notification logic (email, push notifications, etc.)
        // This demonstrates the Observer pattern for notification system
    }
}
