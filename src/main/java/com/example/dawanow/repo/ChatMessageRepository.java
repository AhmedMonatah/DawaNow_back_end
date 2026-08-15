package com.example.dawanow.repo;

import com.example.dawanow.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    List<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    void deleteByConversationId(Long conversationId);
}
