package com.hr.ai.controller;

import com.hr.ai.dto.ActionTaskRequest;
import com.hr.ai.dto.ActionTaskResponse;
import com.hr.ai.dto.ApiResponse;
import com.hr.ai.dto.EmployeeProfileResponse;
import com.hr.ai.service.ActionTaskService;
import com.hr.ai.service.HrDataQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmployeeController {

    private final HrDataQueryService hrDataQueryService;

    @GetMapping("/employees/{employeeId}/profile")
    public ApiResponse<EmployeeProfileResponse> profile(@PathVariable String employeeId) {
        return ApiResponse.success(hrDataQueryService.getEmployeeProfile(employeeId));
    }

    @GetMapping("/employees/by-name/{name}/profile")
    public ApiResponse<EmployeeProfileResponse> profileByName(@PathVariable String name) {
        return ApiResponse.success(hrDataQueryService.getEmployeeProfileByName(name));
    }
}
