package cn.enterprise.kb.document;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "kb_document")
class DocumentEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long knowledgeBaseId;
    long uploadedBy;
    String originalName;
    String objectKey;
    String remoteName;
    long byteSize;
    @Enumerated(EnumType.STRING)
    DocumentService.Status status;
    boolean storageReady;
    boolean deleteRequested;
    boolean remoteWriteAttempted;
    String remoteId;
    String remoteBatch;
    String errorMessage;
    Instant nextAttemptAt;
    Instant indexStartedAt;
    int attempts;
    Instant createdAt;
    Instant updatedAt;

    protected DocumentEntry() {}
}
