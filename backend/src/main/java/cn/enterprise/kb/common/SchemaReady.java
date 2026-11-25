package cn.enterprise.kb.common;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaReady {
    private final JdbcTemplate jdbc;
    private volatile boolean documents;
    private volatile boolean chat;

    SchemaReady(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean documents() { return documents; }
    public boolean chat() { return chat; }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    void check() {
        documents = tableExists("kb_document");
        chat = tableExists("chat_message");
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?)",
                Boolean.class, table));
    }
}
