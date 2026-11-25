package cn.enterprise.kb.chat;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_message_source")
class ChatCitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long messageId;
    long documentId;
    String documentName;
    String remoteDatasetId;
    String remoteDocumentId;
    String remoteSegmentId;
    @Lob
    String excerpt;

    protected ChatCitation() {}
}
