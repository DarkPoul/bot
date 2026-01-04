package com.shiftbot.service;

import com.google.gson.Gson;
import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.AuditEvent;
import com.shiftbot.repository.AuditRepository;
import com.shiftbot.util.MarkdownEscaper;
import com.shiftbot.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.Map;

public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditRepository auditRepository;
    private BotNotificationPort bot;
    private final Long auditChatId;
    private final ZoneId zoneId;
    private final Gson gson = new Gson();

    public AuditService(AuditRepository auditRepository, Long auditChatId, ZoneId zoneId) {
        this.auditRepository = auditRepository;
        this.auditChatId = auditChatId;
        this.zoneId = zoneId;
    }

    public void setBot(BotNotificationPort bot) {
        this.bot = bot;
    }

    public void logEvent(long actorId, String action, String entityType, String entityId, Map<String, Object> details) {
        AuditEvent event = new AuditEvent();
        event.setActorUserId(actorId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setTimestamp(TimeUtils.nowInstant(zoneId));
        event.setDetails(gson.toJson(details));
        auditRepository.save(event);
        if (auditChatId != null && bot != null) {
            try {
                String text = "🛈 " + MarkdownEscaper.escape(action) + " (" + entityType + ")";
                bot.sendMarkdown(auditChatId, text, null);
            } catch (Exception e) {
                log.warn("Failed to notify audit chat", e);
            }
        }
    }
}
