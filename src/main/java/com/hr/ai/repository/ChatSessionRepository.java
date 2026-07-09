package com.hr.ai.repository;

import com.hr.ai.model.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
