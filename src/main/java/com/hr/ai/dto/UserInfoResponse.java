package com.hr.ai.dto;

import com.hr.ai.model.enums.UserRole;
import lombok.Data;

@Data
public class UserInfoResponse {
    private Long id;
    private String username;
    private String name;
    private UserRole role;
    private String departmentId;
    private String departmentName;
    private String employeeId;
}
