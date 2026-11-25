package cn.enterprise.kb.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TurnRepository extends JpaRepository<ChatTurn, Long> {
    Optional<ChatTurn> findByConversationIdAndRequestId(long conversationId, String requestId);
    Page<ChatTurn> findByConversationId(long conversationId, Pageable page);
    List<ChatTurn> findByStatus(ChatStore.Status status);
    boolean existsByConversationId(long conversationId);
    boolean existsByConversationIdAndStatus(long conversationId, ChatStore.Status status);
}
