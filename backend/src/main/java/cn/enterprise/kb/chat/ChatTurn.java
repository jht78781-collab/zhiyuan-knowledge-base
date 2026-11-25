package cn.enterprise.kb.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
class ChatTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long conversationId;
    String requestId;
    String question;
    @Lob
    String answer;
    @Enumerated(EnumType.STRING)
    ChatStore.Status status;
    String remoteMessageId;
    String errorMessage;
    Instant createdAt;

    protected ChatTurn() {}
}
