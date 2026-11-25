package cn.enterprise.kb.knowledge;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "kb_knowledge_base")
class KnowledgeBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String name;
    String description;
    String difyDatasetId;
    String difyAppAlias;
    @Enumerated(EnumType.STRING)
    KnowledgeService.Status status;
    long corpusRevision;
    Instant createdAt;
    Instant updatedAt;

    protected KnowledgeBase() {}
}
