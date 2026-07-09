package com.hr.ai.service;

import com.hr.ai.dto.*;
import com.hr.ai.model.entity.ChatSession;
import com.hr.ai.model.enums.HrQueryIntent;
import com.hr.ai.security.UserPrincipal;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class PreparedChatResult {
    private Long sessionId;
    private String answer;
    private List<SourceReference> sources;
    private List<ChartConfig> charts;
    private QueryTrace trace;
    private List<ActionSuggestion> actions;
}
