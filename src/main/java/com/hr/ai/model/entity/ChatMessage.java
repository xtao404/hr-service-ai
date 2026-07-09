package com.hr.ai.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "LONGTEXT")
    private String sources;

    @Column(columnDefinition = "LONGTEXT")
    private String charts;

    @Column(columnDefinition = "LONGTEXT")
    private String trace;

    @Column(columnDefinition = "LONGTEXT")
    private String actions;

    private LocalDateTime createdAt = LocalDateTime.now();
}
