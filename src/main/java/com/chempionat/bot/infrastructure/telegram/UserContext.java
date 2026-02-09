package com.chempionat.bot.infrastructure.telegram;

import com.chempionat.bot.domain.enums.TournamentType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores user conversation state for multi-step commands
 */
@Data
public class UserContext {
    private static final Map<Long, UserContext> contexts = new ConcurrentHashMap<>();
    
    private Long userId;
    private String currentCommand;
    private Map<String, Object> data = new HashMap<>();
    
    // Admin impersonation support
    private Long impersonatedOrganizerId;
    
    public static UserContext get(Long userId) {
        return contexts.computeIfAbsent(userId, id -> {
            UserContext ctx = new UserContext();
            ctx.setUserId(id);
            return ctx;
        });
    }
    
    public static void clear(Long userId) {
        contexts.remove(userId);
    }
    
    public void setData(String key, Object value) {
        data.put(key, value);
    }
    
    public Object getData(String key) {
        return data.get(key);
    }
    
    public String getDataAsString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    public Integer getDataAsInteger(String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Long getDataAsLong(String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Boolean getDataAsBoolean(String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    public void clearData() {
        data.clear();
        currentCommand = null;
    }
    
    // Impersonation methods
    public void setImpersonatedOrganizer(Long organizerId) {
        this.impersonatedOrganizerId = organizerId;
    }
    
    public Long getImpersonatedOrganizerId() {
        return impersonatedOrganizerId;
    }
    
    public boolean isImpersonating() {
        return impersonatedOrganizerId != null;
    }
    
    public void exitImpersonation() {
        this.impersonatedOrganizerId = null;
    }
}
