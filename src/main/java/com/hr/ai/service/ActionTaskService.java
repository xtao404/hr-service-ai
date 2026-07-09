package com.hr.ai.service;

import com.hr.ai.dto.ActionTaskRequest;
import com.hr.ai.dto.ActionTaskResponse;
import com.hr.ai.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ActionTaskService {

    private static final AtomicLong TASK_SEQ = new AtomicLong(1000);

    private final PermissionService permissionService;

    public ActionTaskResponse createTask(ActionTaskRequest request) {
        permissionService.currentUser();

        String taskId = "HR-TASK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + TASK_SEQ.incrementAndGet();

        ActionTaskResponse response = new ActionTaskResponse();
        response.setTaskId(taskId);
        response.setStatus("CREATED");
        response.setMessage(buildMessage(request, taskId));
        return response;
    }

    private String buildMessage(ActionTaskRequest request, String taskId) {
        String target = request.getEmployeeName() != null ? request.getEmployeeName() : "相关事项";
        return String.format("已创建模拟工单 %s：「%s」（%s），可在 HR 系统中跟进执行。",
                taskId, request.getTitle(), target);
    }
}
