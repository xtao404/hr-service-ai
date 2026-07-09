package com.hr.ai.service;

import com.hr.ai.config.RagProperties;
import com.hr.ai.model.entity.KnowledgeDocument;
import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索服务 - 生产环境可替换为 Milvus / Pinecone / pgvector
 */
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final KnowledgeDocumentRepository documentRepository;
    private final RagProperties ragProperties;

    private static final List<String> HR_KEYWORDS = List.of(
            "年假", "假期", "请假", "病假", "事假", "调休", "余额",
            "考勤", "迟到", "早退", "旷工", "加班", "工时",
            "薪酬", "工资", "薪资", "奖金", "调薪", "13薪",
            "福利", "五险一金", "公积金", "社保", "体检", "保险",
            "入职", "离职", "辞职", "offer", "劳动合同",
            "绩效", "考核", "晋升", "培训"
    );

    private static final Map<String, KnowledgeCategory> KEYWORD_CATEGORY = Map.ofEntries(
            Map.entry("年假", KnowledgeCategory.LEAVE),
            Map.entry("假期", KnowledgeCategory.LEAVE),
            Map.entry("请假", KnowledgeCategory.LEAVE),
            Map.entry("病假", KnowledgeCategory.LEAVE),
            Map.entry("事假", KnowledgeCategory.LEAVE),
            Map.entry("余额", KnowledgeCategory.LEAVE),
            Map.entry("考勤", KnowledgeCategory.ATTENDANCE),
            Map.entry("迟到", KnowledgeCategory.ATTENDANCE),
            Map.entry("早退", KnowledgeCategory.ATTENDANCE),
            Map.entry("旷工", KnowledgeCategory.ATTENDANCE),
            Map.entry("加班", KnowledgeCategory.ATTENDANCE),
            Map.entry("薪酬", KnowledgeCategory.SALARY_POLICY),
            Map.entry("工资", KnowledgeCategory.SALARY_POLICY),
            Map.entry("薪资", KnowledgeCategory.SALARY_POLICY),
            Map.entry("奖金", KnowledgeCategory.SALARY_POLICY),
            Map.entry("调薪", KnowledgeCategory.SALARY_POLICY),
            Map.entry("福利", KnowledgeCategory.BENEFITS),
            Map.entry("五险一金", KnowledgeCategory.BENEFITS),
            Map.entry("公积金", KnowledgeCategory.BENEFITS),
            Map.entry("社保", KnowledgeCategory.BENEFITS),
            Map.entry("体检", KnowledgeCategory.BENEFITS),
            Map.entry("入职", KnowledgeCategory.ONBOARDING),
            Map.entry("离职", KnowledgeCategory.OFFBOARDING),
            Map.entry("辞职", KnowledgeCategory.OFFBOARDING)
    );

    public List<ScoredDocument> search(String query) {
        List<KnowledgeDocument> allDocs = documentRepository.findAll();
        if (allDocs.isEmpty()) {
            return List.of();
        }

        List<ScoredDocument> scored = allDocs.stream()
                .map(doc -> new ScoredDocument(doc, computeSimilarity(query, doc)))
                .sorted(Comparator.comparingDouble(ScoredDocument::similarity).reversed())
                .collect(Collectors.toList());

        List<ScoredDocument> matched = scored.stream()
                .filter(sd -> sd.similarity() >= ragProperties.getSimilarityThreshold())
                .limit(ragProperties.getTopK())
                .collect(Collectors.toList());

        if (!matched.isEmpty()) {
            return matched;
        }

        if (ragProperties.isCategoryFallbackEnabled()) {
            List<ScoredDocument> categoryFallback = searchByCategory(query, allDocs);
            if (!categoryFallback.isEmpty()) {
                return categoryFallback;
            }
        }

        if (ragProperties.isLowScoreFallbackEnabled()) {
            ScoredDocument best = scored.get(0);
            if (best.similarity() > 0) {
                return List.of(best);
            }
        }

        return List.of();
    }

    /** top-1 相似度是否达到可回答阈值 */
    public boolean isConfidentEnough(List<ScoredDocument> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return false;
        }
        return contexts.get(0).similarity() >= ragProperties.getMinConfidenceForAnswer();
    }

    private List<ScoredDocument> searchByCategory(String query, List<KnowledgeDocument> allDocs) {
        Set<KnowledgeCategory> categories = new LinkedHashSet<>();
        for (Map.Entry<String, KnowledgeCategory> entry : KEYWORD_CATEGORY.entrySet()) {
            if (query.contains(entry.getKey())) {
                categories.add(entry.getValue());
            }
        }
        if (categories.isEmpty()) {
            return List.of();
        }

        return allDocs.stream()
                .filter(doc -> categories.contains(doc.getCategory()))
                .map(doc -> new ScoredDocument(doc, ragProperties.getMinConfidenceForAnswer()))
                .limit(ragProperties.getTopK())
                .collect(Collectors.toList());
    }

    public void indexDocument(KnowledgeDocument document) {
        document.setEmbedding(computeEmbedding(document.getTitle() + " " + document.getContent()));
        documentRepository.save(document);
    }

    private double computeSimilarity(String query, KnowledgeDocument doc) {
        String searchText = doc.getTitle() + " " + doc.getContent();
        Set<String> queryTokens = tokenize(query);
        Set<String> docTokens = tokenize(searchText);
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(docTokens);
        if (intersection.isEmpty()) {
            return titleKeywordBonus(query, doc);
        }

        Set<String> union = new HashSet<>(queryTokens);
        union.addAll(docTokens);
        double jaccard = (double) intersection.size() / union.size();

        double titleBonus = titleKeywordBonus(query, doc);
        double keywordBonus = keywordMatchBonus(query, searchText);

        return Math.min(1.0, jaccard + titleBonus + keywordBonus);
    }

    private double titleKeywordBonus(String query, KnowledgeDocument doc) {
        double bonus = 0.0;
        String title = doc.getTitle();
        for (String keyword : HR_KEYWORDS) {
            if (query.contains(keyword) && title.contains(keyword)) {
                bonus += 0.15;
            }
        }
        return Math.min(0.45, bonus);
    }

    private double keywordMatchBonus(String query, String text) {
        double bonus = 0.0;
        for (String keyword : HR_KEYWORDS) {
            if (query.contains(keyword) && text.contains(keyword)) {
                bonus += 0.08;
            }
        }
        return Math.min(0.4, bonus);
    }

    private String computeEmbedding(String text) {
        return String.join(",", tokenize(text));
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = text.toLowerCase().replaceAll("\\s+", "");

        String[] segments = normalized.split("[，。、；：！？,.;:!?\\n（）()【】\\[\\]\"'\\-—]+");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.length() >= 2) {
                tokens.add(segment);
            }
            extractChineseBigrams(segment, tokens);
            for (String keyword : HR_KEYWORDS) {
                if (segment.contains(keyword)) {
                    tokens.add(keyword);
                }
            }
        }

        for (String keyword : HR_KEYWORDS) {
            if (normalized.contains(keyword)) {
                tokens.add(keyword);
            }
        }

        return tokens;
    }

    private void extractChineseBigrams(String segment, Set<String> tokens) {
        for (int i = 0; i < segment.length() - 1; i++) {
            char c1 = segment.charAt(i);
            char c2 = segment.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                tokens.add("" + c1 + c2);
            }
        }
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    public record ScoredDocument(KnowledgeDocument document, double similarity) {}
}
