package cn.enterprise.kb.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberRepository extends JpaRepository<KnowledgeMember, Long> {
    Optional<KnowledgeMember> findByKnowledgeBaseIdAndUserId(long kbId, long userId);
    List<KnowledgeMember> findByUserIdAndKnowledgeBaseIdIn(long userId, Collection<Long> ids);
    Page<KnowledgeMember> findByKnowledgeBaseId(long id, Pageable page);
}
