package com.yizhaoqi.smartpai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yizhaoqi.smartpai.config.MilvusProperties;
import com.yizhaoqi.smartpai.entity.KnowledgeChunkDocument;
import com.yizhaoqi.smartpai.entity.VectorSearchRequest;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 Milvus 的向量存储实现。
 * <p>
 * 负责知识分块向量的写入、语义检索和删除，并在查询阶段把业务侧的权限条件
 * 转换为 Milvus 过滤表达式，保证向量召回结果与系统权限模型保持一致。
 */
@Service
public class MilvusVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStore.class);
    private static final List<String> OUTPUT_FIELDS = List.of(
            "fileMd5", "chunkId", "textContent", "modelVersion", "userId", "orgTag", "isPublic"
    );

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties milvusProperties;
    private final Gson gson = new Gson();

    public MilvusVectorStore(MilvusClientV2 milvusClient, MilvusProperties milvusProperties) {
        this.milvusClient = milvusClient;
        this.milvusProperties = milvusProperties;
    }

    @Override
    public void upsert(List<KnowledgeChunkDocument> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }

        try {
            List<JsonObject> rows = documents.stream()
                    .map(this::toJsonObject)
                    .toList();

            UpsertReq request = UpsertReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .data(rows)
                    .build();

            milvusClient.upsert(request);
            logger.info("Milvus 批量 upsert 完成，文档数: {}", documents.size());
        } catch (Exception e) {
            logger.error("Milvus 批量 upsert 失败，文档数: {}", documents.size(), e);
            throw new RuntimeException("Milvus 批量 upsert 失败", e);
        }
    }

    /**
     * 执行一次带权限过滤的向量检索。
     */
    @Override
    public List<KnowledgeChunkDocument> search(VectorSearchRequest request) {
        if (request == null || request.queryVector() == null || request.queryVector().length == 0) {
            return Collections.emptyList();
        }

        try {
            var builder = SearchReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .annsField("vector")
                    .data(Collections.singletonList(new FloatVec(request.queryVector())))
                    .topK(request.topK())
                    .outputFields(OUTPUT_FIELDS)
                    .searchParams(buildSearchParams());

            String filter = buildFilter(request);
            if (StringUtils.hasText(filter)) {
                builder.filter(filter);
            }

            SearchResp searchResp = milvusClient.search(builder.build());
            List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
            if (CollectionUtils.isEmpty(results)) {
                return Collections.emptyList();
            }

            List<KnowledgeChunkDocument> documents = new ArrayList<>();
            for (SearchResp.SearchResult result : results.get(0)) {
                documents.add(toKnowledgeChunkDocument(result));
            }
            return documents;
        } catch (Exception e) {
            logger.error("Milvus 检索失败", e);
            throw new RuntimeException("Milvus 检索失败", e);
        }
    }

    @Override
    public void deleteByFileMd5(String fileMd5) {
        try {
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .filter("fileMd5 == \"" + escapeFilterString(fileMd5) + "\"")
                    .build());
        } catch (Exception e) {
            logger.error("Milvus 删除 fileMd5={} 失败", fileMd5, e);
            throw new RuntimeException("Milvus 删除文档失败", e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    // Milvus v2 delete 需要 filter，这里使用匹配全部主键的方式实现全量删除。
                    .filter("id like \"%\"")
                    .build());
        } catch (Exception e) {
            logger.error("Milvus 清空集合失败", e);
            throw new RuntimeException("Milvus 清空集合失败", e);
        }
    }

    /**
     * 将统一知识块模型转换为 Milvus upsert 所需的 JSON 行结构。
     */
    private JsonObject toJsonObject(KnowledgeChunkDocument document) {
        JsonObject row = new JsonObject();
        row.addProperty("id", document.getId());
        row.addProperty("fileMd5", normalizeString(document.getFileMd5()));
        row.addProperty("chunkId", document.getChunkId() == null ? 0L : document.getChunkId().longValue());
        row.addProperty("textContent", truncate(normalizeString(document.getTextContent()), milvusProperties.getTextMaxLength()));
        row.addProperty("modelVersion", normalizeString(document.getModelVersion()));
        row.addProperty("userId", normalizeString(document.getUserId()));
        row.addProperty("orgTag", normalizeString(document.getOrgTag()));
        row.addProperty("isPublic", document.isPublic());
        row.add("vector", gson.toJsonTree(document.getVector()));
        return row;
    }

    /**
     * 构造 Milvus 查询参数。
     * <p>
     * HNSW 索引下额外指定 ef，以在召回效果和查询性能之间做平衡。
     */
    private Map<String, Object> buildSearchParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("metric_type", milvusProperties.getMetricType().toUpperCase(Locale.ROOT));
        if ("HNSW".equalsIgnoreCase(milvusProperties.getIndexType())) {
            params.put("ef", milvusProperties.getSearchEf());
        }
        return params;
    }

    /**
     * 根据当前用户、组织标签和公开状态构造 Milvus 过滤表达式。
     */
    private String buildFilter(VectorSearchRequest request) {
        if (request.publicOnly() || !StringUtils.hasText(request.userId())) {
            return "isPublic == true";
        }

        List<String> conditions = new ArrayList<>();
        conditions.add("userId == \"" + escapeFilterString(request.userId()) + "\"");
        conditions.add("isPublic == true");
        if (!CollectionUtils.isEmpty(request.orgTags())) {
            String orgTagExpr = request.orgTags().stream()
                    .filter(StringUtils::hasText)
                    .map(tag -> "\"" + escapeFilterString(tag) + "\"")
                    .collect(Collectors.joining(", "));
            if (StringUtils.hasText(orgTagExpr)) {
                conditions.add("orgTag in [" + orgTagExpr + "]");
            }
        }
        return "(" + String.join(" or ", conditions) + ")";
    }

    /**
     * 将 Milvus 检索结果映射为应用层统一知识块对象。
     */
    private KnowledgeChunkDocument toKnowledgeChunkDocument(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setId(String.valueOf(result.getId()));
        document.setFileMd5(asString(entity.get("fileMd5")));
        document.setChunkId(asInteger(entity.get("chunkId")));
        document.setTextContent(asString(entity.get("textContent")));
        document.setModelVersion(asString(entity.get("modelVersion")));
        document.setUserId(asString(entity.get("userId")));
        document.setOrgTag(asString(entity.get("orgTag")));
        document.setPublic(asBoolean(entity.get("isPublic")));
        document.setScore(result.getScore());
        return document;
    }

    /**
     * 将 null 统一收敛为空字符串，避免 Milvus 字符串字段写入报错。
     */
    private String normalizeString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 控制文本字段长度，避免超过集合 schema 中定义的最大长度。
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 转义过滤表达式中的特殊字符，避免构造出的 Milvus 语句非法。
     */
    private String escapeFilterString(String value) {
        return normalizeString(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 兼容 SDK 返回 number / string 两种类型的 chunkId。
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    /**
     * 兼容 SDK 返回 boolean / string 两种类型的布尔值。
     */
    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}
