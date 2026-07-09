package com.hr.ai.dto;

import com.hr.ai.model.enums.UserRole;
import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String name;
    private UserRole role;
    private String departmentId;
    private String departmentName;
}
