package cn.enterprise.kb.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_conversation")
class ChatConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long ownerId;
    long knowledgeBaseId;
    String title;
    String remoteConversationId;
    long corpusRevision;
    Instant createdAt;
    Instant updatedAt;
    Instant deletedAt;

    protected ChatConversation() {}
}
