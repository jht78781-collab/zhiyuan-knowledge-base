package cn.enterprise.kb.chat;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CitationRepository extends JpaRepository<ChatCitation, Long> {
    List<ChatCitation> findByMessageIdInOrderById(Collection<Long> messageIds);
}
