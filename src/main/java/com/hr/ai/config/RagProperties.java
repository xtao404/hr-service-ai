package com.hr.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hr.ai.rag")
public class RagProperties {

    /** 检索返回的最大文档数 */
    private int topK = 3;

    /** 向量/关键词相似度下限，低于此值的文档不进入候选 */
    private double similarityThreshold = 0.15;

    /**
     * 回答所需的最低置信度（top-1 相似度）。
     * 低于此值直接拒答，不调用大模型，避免基于弱相关文档编造。
     */
    private double minConfidenceForAnswer = 0.15;

    /** 相似度不足时，是否按 HR 关键词分类回退检索 */
    private boolean categoryFallbackEnabled = true;

    /**
     * 是否在无匹配时强制返回最高分文档（易导致幻觉，生产建议关闭）。
     */
    private boolean lowScoreFallbackEnabled = false;
}
