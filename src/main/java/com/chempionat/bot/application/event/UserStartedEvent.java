package com.chempionat.bot.application.event;

import com.chempionat.bot.domain.model.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserStartedEvent extends ApplicationEvent {
    
    private final User user;
    
    public UserStartedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
