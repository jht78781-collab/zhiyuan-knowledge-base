package cn.enterprise.kb.knowledge;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface KnowledgeRepository extends JpaRepository<KnowledgeBase, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KnowledgeBase k where k.id = :id")
    Optional<KnowledgeBase> lock(long id);

    Page<KnowledgeBase> findByStatusNot(KnowledgeService.Status status, Pageable pageable);

    @Query("""
            select k from KnowledgeBase k where k.status = :active and exists
            (select 1 from KnowledgeMember m where m.knowledgeBaseId = k.id and m.userId = :userId)
            """)
    Page<KnowledgeBase> visible(long userId, KnowledgeService.Status active, Pageable pageable);
}
