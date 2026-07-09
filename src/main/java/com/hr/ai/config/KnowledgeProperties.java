package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.knowledge")
public class KnowledgeProperties {

    private int maxChunkSize = 2000;

    private String allowedExtensions = "pdf,doc,docx";

    public Set<String> allowedExtensionSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
