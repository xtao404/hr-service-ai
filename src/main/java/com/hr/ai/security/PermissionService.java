package com.hr.ai.security;

import com.hr.ai.model.enums.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PermissionService {

    public UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("未登录或会话已过期");
        }
        return principal;
    }

    public void checkDepartmentAccess(String targetDepartmentId) {
        UserPrincipal user = currentUser();
        if (user.getRole() == UserRole.HR_ADMIN || user.getRole() == UserRole.HRBP) {
            return;
        }
        if (user.getRole() == UserRole.MANAGER
                && user.getDepartmentId() != null
                && user.getDepartmentId().equals(targetDepartmentId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该部门数据");
    }

    public void checkEmployeeAccess(String targetEmployeeId) {
        UserPrincipal user = currentUser();
        if (user.getRole() == UserRole.HR_ADMIN || user.getRole() == UserRole.HRBP) {
            return;
        }
        if (user.getEmployeeId() != null && user.getEmployeeId().equals(targetEmployeeId)) {
            return;
        }
        if (user.getRole() == UserRole.MANAGER) {
            return;
        }
        throw new AccessDeniedException("无权访问该员工数据");
    }

    public boolean canViewSalary() {
        UserRole role = currentUser().getRole();
        return role == UserRole.HR_ADMIN || role == UserRole.HRBP;
    }
}
