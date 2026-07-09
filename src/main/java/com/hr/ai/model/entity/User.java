package com.hr.ai.model.entity;

import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.model.enums.UserRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String name;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String departmentId;

    private String departmentName;

    private String employeeId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
