package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.preset-query")
public class PresetQueryProperties {

    /** 是否启用预设查询（个人/指定员工/部门/公司等固定 Repository 查询） */
    private boolean enabled = false;
}
