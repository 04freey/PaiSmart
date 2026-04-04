package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.config.SearchProperties;
import com.yizhaoqi.smartpai.entity.KnowledgeChunkDocument;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.entity.VectorSearchRequest;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * 混合搜索服务，基于 Milvus 向量检索 + MySQL 文本召回。
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    private final VectorStore vectorStore;
    private final TextSearchService textSearchService;
    private final EmbeddingClient embeddingClient;
    private final UserRepository userRepository;
    private final OrgTagCacheService orgTagCacheService;
    private final FileUploadRepository fileUploadRepository;
    private final SearchProperties searchProperties;

    public HybridSearchService(VectorStore vectorStore,
                               TextSearchService textSearchService,
                               EmbeddingClient embeddingClient,
                               UserRepository userRepository,
                               OrgTagCacheService orgTagCacheService,
                               FileUploadRepository fileUploadRepository,
                               SearchProperties searchProperties) {
        this.vectorStore = vectorStore;
        this.textSearchService = textSearchService;
        this.embeddingClient = embeddingClient;
        this.userRepository = userRepository;
        this.orgTagCacheService = orgTagCacheService;
        this.fileUploadRepository = fileUploadRepository;
        this.searchProperties = searchProperties;
    }

    public List<SearchResult> searchWithPermission(String query, String userId, int topK) {
        logger.debug("开始带权限搜索，query={}, userId={}", query, userId);
        String userDbId = getUserDbId(userId);
        List<String> userEffectiveTags = getUserEffectiveOrgTags(userId);
        return performSearch(query, topK, userDbId, userEffectiveTags, false);
    }

    /**
     * 匿名搜索仅检索公开内容。
     */
    public List<SearchResult> search(String query, int topK) {
        logger.debug("开始公开混合检索，query={}, topK={}", query, topK);
        return performSearch(query, topK, null, Collections.emptyList(), true);
    }

    private List<SearchResult> performSearch(String query, int topK, String userDbId, List<String> userEffectiveTags, boolean publicOnly) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return Collections.emptyList();
        }

        int vectorRecallK = Math.max(searchProperties.getVectorRecallK(), topK * 4);
        int textRecallK = Math.max(searchProperties.getTextRecallK(), topK * 4);

        List<KnowledgeChunkDocument> vectorHits = searchByVector(query, vectorRecallK, userDbId, userEffectiveTags, publicOnly);
        List<KnowledgeChunkDocument> textHits = searchByText(query, textRecallK, userDbId, userEffectiveTags, publicOnly);

        boolean hasVectorHits = !vectorHits.isEmpty();
        boolean hasTextHits = !textHits.isEmpty();
        if (!hasVectorHits && !hasTextHits) {
            return Collections.emptyList();
        }

        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        mergeVectorHits(vectorHits, merged, userDbId, userEffectiveTags, publicOnly);
        mergeTextHits(query, textHits, merged, userDbId, userEffectiveTags, publicOnly);

        if (merged.isEmpty()) {
            return Collections.emptyList();
        }

        normalizeScores(merged.values(), RetrievalCandidate::getVectorScore, RetrievalCandidate::setVectorScore);
        normalizeScores(merged.values(), RetrievalCandidate::getTextScore, RetrievalCandidate::setTextScore);

        List<SearchResult> results = merged.values().stream()
                .peek(candidate -> candidate.setFinalScore(calculateFinalScore(candidate, hasVectorHits, hasTextHits)))
                .sorted(Comparator.comparingDouble(RetrievalCandidate::getFinalScore).reversed())
                .limit(topK)
                .map(candidate -> new SearchResult(
                        candidate.getFileMd5(),
                        candidate.getChunkId(),
                        candidate.getTextContent(),
                        candidate.getFinalScore(),
                        candidate.getUserId(),
                        candidate.getOrgTag(),
                        candidate.isPublic()
                ))
                .collect(Collectors.toCollection(ArrayList::new));

        attachFileNames(results);
        return results;
    }

    private List<KnowledgeChunkDocument> searchByVector(String query, int topK, String userDbId, List<String> userEffectiveTags, boolean publicOnly) {
        float[] vector = embed(query);
        if (vector == null) {
            return Collections.emptyList();
        }

        try {
            return vectorStore.search(new VectorSearchRequest(vector, topK, userDbId, userEffectiveTags, publicOnly));
        } catch (Exception e) {
            logger.error("向量检索失败，回退为纯文本搜索", e);
            return Collections.emptyList();
        }
    }

    private List<KnowledgeChunkDocument> searchByText(String query, int topK, String userDbId, List<String> userEffectiveTags, boolean publicOnly) {
        try {
            if (publicOnly) {
                return textSearchService.searchPublic(query, topK);
            }
            return textSearchService.searchAccessible(query, userDbId, userEffectiveTags, topK);
        } catch (Exception e) {
            logger.error("文本检索失败", e);
            return Collections.emptyList();
        }
    }

    private void mergeVectorHits(List<KnowledgeChunkDocument> vectorHits,
                                 Map<String, RetrievalCandidate> merged,
                                 String userDbId,
                                 List<String> userEffectiveTags,
                                 boolean publicOnly) {
        for (KnowledgeChunkDocument hit : vectorHits) {
            if (!isAccessible(hit, userDbId, userEffectiveTags, publicOnly)) {
                continue;
            }
            RetrievalCandidate candidate = merged.computeIfAbsent(buildCandidateKey(hit), ignored -> RetrievalCandidate.from(hit));
            candidate.enrich(hit);
            candidate.setVectorScore(Math.max(candidate.getVectorScore(), safeScore(hit.getScore())));
        }
    }

    private void mergeTextHits(String query,
                               List<KnowledgeChunkDocument> textHits,
                               Map<String, RetrievalCandidate> merged,
                               String userDbId,
                               List<String> userEffectiveTags,
                               boolean publicOnly) {
        for (KnowledgeChunkDocument hit : textHits) {
            if (!isAccessible(hit, userDbId, userEffectiveTags, publicOnly)) {
                continue;
            }
            RetrievalCandidate candidate = merged.computeIfAbsent(buildCandidateKey(hit), ignored -> RetrievalCandidate.from(hit));
            candidate.enrich(hit);
            double dbTextScore = safeScore(hit.getScore());
            double heuristicScore = computeTextScore(query, hit.getTextContent());
            candidate.setTextScore(Math.max(candidate.getTextScore(), Math.max(dbTextScore, heuristicScore)));
        }
    }

    private double calculateFinalScore(RetrievalCandidate candidate, boolean hasVectorHits, boolean hasTextHits) {
        if (hasVectorHits && hasTextHits) {
            return candidate.getVectorScore() * searchProperties.getVectorWeight()
                    + candidate.getTextScore() * searchProperties.getTextWeight();
        }
        if (hasVectorHits) {
            return candidate.getVectorScore();
        }
        return candidate.getTextScore();
    }

    private boolean isAccessible(KnowledgeChunkDocument document, String userDbId, List<String> userEffectiveTags, boolean publicOnly) {
        if (document == null) {
            return false;
        }
        if (publicOnly) {
            return document.isPublic();
        }
        if (document.isPublic()) {
            return true;
        }
        if (StringUtils.hasText(userDbId) && userDbId.equals(document.getUserId())) {
            return true;
        }
        return !CollectionUtils.isEmpty(userEffectiveTags) && StringUtils.hasText(document.getOrgTag())
                && userEffectiveTags.contains(document.getOrgTag());
    }

    private String buildCandidateKey(KnowledgeChunkDocument document) {
        return KnowledgeChunkDocument.buildId(document.getFileMd5(), document.getChunkId());
    }

    private float[] embed(String text) {
        try {
            List<float[]> vectors = embeddingClient.embed(List.of(text));
            if (CollectionUtils.isEmpty(vectors)) {
                logger.warn("查询向量生成为空");
                return null;
            }
            return vectors.get(0);
        } catch (Exception e) {
            logger.error("生成查询向量失败", e);
            return null;
        }
    }

    private double computeTextScore(String query, String textContent) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(textContent)) {
            return 0d;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String normalizedText = textContent.toLowerCase(Locale.ROOT);
        int firstIndex = normalizedText.indexOf(normalizedQuery);
        if (firstIndex < 0) {
            return 0d;
        }

        int occurrences = countOccurrences(normalizedText, normalizedQuery);
        double exactMatchScore = normalizedText.equals(normalizedQuery) ? 1.0d : 0d;
        double prefixScore = firstIndex == 0 ? 0.25d : 0d;
        double occurrenceScore = Math.min(0.30d, occurrences * 0.08d);
        double positionScore = Math.max(0d, 0.30d - (Math.min(firstIndex, 300) / 1000.0d));
        double densityScore = Math.min(0.15d, normalizedQuery.length() / (double) Math.max(normalizedText.length(), 1));
        return 0.2d + exactMatchScore + prefixScore + occurrenceScore + positionScore + densityScore;
    }

    private int countOccurrences(String text, String query) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = text.indexOf(query, fromIndex)) >= 0) {
            count++;
            fromIndex += query.length();
        }
        return count;
    }

    private void normalizeScores(Iterable<RetrievalCandidate> candidates,
                                 ToDoubleFunction<RetrievalCandidate> getter,
                                 ScoreSetter setter) {
        List<RetrievalCandidate> list = new ArrayList<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (RetrievalCandidate candidate : candidates) {
            list.add(candidate);
            double value = getter.applyAsDouble(candidate);
            if (value > 0d) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        if (list.isEmpty()) {
            return;
        }

        if (min == Double.POSITIVE_INFINITY) {
            list.forEach(candidate -> setter.accept(candidate, 0d));
            return;
        }

        if (Double.compare(min, max) == 0) {
            list.forEach(candidate -> setter.accept(candidate, getter.applyAsDouble(candidate) > 0d ? 1d : 0d));
            return;
        }

        for (RetrievalCandidate candidate : list) {
            double value = getter.applyAsDouble(candidate);
            setter.accept(candidate, value <= 0d ? 0d : (value - min) / (max - min));
        }
    }

    private double safeScore(Float score) {
        return score == null ? 0d : score.doubleValue();
    }

    /**
     * 获取用户的有效组织标签（包含层级关系）。
     */
    private List<String> getUserEffectiveOrgTags(String userId) {
        logger.debug("获取用户有效组织标签，用户ID: {}", userId);
        try {
            User user;
            try {
                Long userIdLong = Long.parseLong(userId);
                user = userRepository.findById(userIdLong)
                        .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
            } catch (NumberFormatException e) {
                user = userRepository.findByUsername(userId)
                        .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
            }
            return orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
        } catch (Exception e) {
            logger.error("获取用户有效组织标签失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户数据库 ID 用于权限过滤。
     */
    private String getUserDbId(String userId) {
        logger.debug("获取用户数据库ID，用户ID: {}", userId);
        try {
            User user;
            try {
                Long userIdLong = Long.parseLong(userId);
                user = userRepository.findById(userIdLong)
                        .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
                return user.getId().toString();
            } catch (NumberFormatException e) {
                user = userRepository.findByUsername(userId)
                        .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
                return user.getId().toString();
            }
        } catch (Exception e) {
            logger.error("获取用户数据库ID失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取用户数据库ID失败", e);
        }
    }

    private void attachFileNames(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        try {
            Set<String> md5Set = results.stream()
                    .map(SearchResult::getFileMd5)
                    .collect(Collectors.toSet());
            List<FileUpload> uploads = fileUploadRepository.findByFileMd5In(new ArrayList<>(md5Set));
            Map<String, String> md5ToName = uploads.stream()
                    .collect(Collectors.toMap(FileUpload::getFileMd5, FileUpload::getFileName));
            results.forEach(result -> result.setFileName(md5ToName.get(result.getFileMd5())));
        } catch (Exception e) {
            logger.error("补充文件名失败", e);
        }
    }

    @FunctionalInterface
    private interface ScoreSetter {
        void accept(RetrievalCandidate candidate, double score);
    }

    private static class RetrievalCandidate {
        private final String fileMd5;
        private final Integer chunkId;
        private String textContent;
        private String userId;
        private String orgTag;
        private boolean isPublic;
        private double vectorScore;
        private double textScore;
        private double finalScore;

        private RetrievalCandidate(String fileMd5, Integer chunkId, String textContent,
                                   String userId, String orgTag, boolean isPublic) {
            this.fileMd5 = fileMd5;
            this.chunkId = chunkId;
            this.textContent = textContent;
            this.userId = userId;
            this.orgTag = orgTag;
            this.isPublic = isPublic;
        }

        static RetrievalCandidate from(KnowledgeChunkDocument document) {
            return new RetrievalCandidate(
                    document.getFileMd5(),
                    document.getChunkId(),
                    document.getTextContent(),
                    document.getUserId(),
                    document.getOrgTag(),
                    document.isPublic()
            );
        }

        void enrich(KnowledgeChunkDocument document) {
            if (!StringUtils.hasText(this.textContent) && StringUtils.hasText(document.getTextContent())) {
                this.textContent = document.getTextContent();
            }
            if (!StringUtils.hasText(this.userId) && StringUtils.hasText(document.getUserId())) {
                this.userId = document.getUserId();
            }
            if (!StringUtils.hasText(this.orgTag) && StringUtils.hasText(document.getOrgTag())) {
                this.orgTag = document.getOrgTag();
            }
            this.isPublic = this.isPublic || document.isPublic();
        }

        public String getFileMd5() {
            return fileMd5;
        }

        public Integer getChunkId() {
            return chunkId;
        }

        public String getTextContent() {
            return textContent;
        }

        public String getUserId() {
            return userId;
        }

        public String getOrgTag() {
            return orgTag;
        }

        public boolean isPublic() {
            return isPublic;
        }

        public double getVectorScore() {
            return vectorScore;
        }

        public void setVectorScore(double vectorScore) {
            this.vectorScore = vectorScore;
        }

        public double getTextScore() {
            return textScore;
        }

        public void setTextScore(double textScore) {
            this.textScore = textScore;
        }

        public double getFinalScore() {
            return finalScore;
        }

        public void setFinalScore(double finalScore) {
            this.finalScore = finalScore;
        }
    }
}
