package cn.enterprise.kb.chat;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ConversationRepository extends JpaRepository<ChatConversation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> lock(long id);

    Page<ChatConversation> findByOwnerIdAndKnowledgeBaseIdAndDeletedAtIsNull(long owner, long kbId, Pageable page);
}
