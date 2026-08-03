package com.example.dawanow.repo;

import com.example.dawanow.entity.ChatConversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /**
     * Each user keeps exactly one conversation. Ordering by id keeps the same
     * row stable for callers even if older rows exist from earlier versions.
     */
    Optional<ChatConversation> findFirstByUserIdOrderByIdAsc(Long userId);
}
