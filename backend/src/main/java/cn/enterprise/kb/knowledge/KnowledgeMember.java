package cn.enterprise.kb.knowledge;

import jakarta.persistence.*;

@Entity
@Table(name = "kb_member")
class KnowledgeMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long knowledgeBaseId;
    long userId;
    @Enumerated(EnumType.STRING)
    KnowledgeService.Permission permission;

    protected KnowledgeMember() {}
}
