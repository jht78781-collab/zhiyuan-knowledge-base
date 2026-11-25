package cn.enterprise.kb.statistics;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_call_log")
class AiCall {
    @Id
    Long messageId;
    @Enumerated(EnumType.STRING)
    UsageService.Status status;
    Long latencyMs;
    Long inputTokens;
    Long outputTokens;
    Long totalTokens;
    String errorCode;
    Instant calledAt;

    protected AiCall() {}
}
