package cn.enterprise.kb.document;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DocumentRepository extends JpaRepository<DocumentEntry, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentEntry d where d.id = :id")
    Optional<DocumentEntry> lock(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DocumentEntry> findByKnowledgeBaseIdAndStatusNotOrderById(long kbId, DocumentService.Status status);
    Optional<DocumentEntry> findFirstByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(Instant time);
    Page<DocumentEntry> findByKnowledgeBaseIdAndStatusNot(long kbId, DocumentService.Status status, Pageable page);
    Page<DocumentEntry> findByKnowledgeBaseIdAndStatus(long kbId, DocumentService.Status status, Pageable page);
    long countByKnowledgeBaseIdAndStatusNot(long kbId, DocumentService.Status status);
    List<DocumentEntry> findByKnowledgeBaseIdAndStatusAndRemoteIdIn(long kbId, DocumentService.Status status, Collection<String> ids);
    boolean existsByKnowledgeBaseIdAndStatus(long kbId, DocumentService.Status status);

    @Query("""
            select d.knowledgeBaseId as kbId, count(d) as available from DocumentEntry d
            where d.knowledgeBaseId in :ids and d.status = :status group by d.knowledgeBaseId
            """)
    List<CountRow> counts(Collection<Long> ids, DocumentService.Status status);

    interface CountRow { long getKbId(); long getAvailable(); }
}
