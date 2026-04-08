package com.yizhaoqi.smartpai.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Milvus 集合初始化器。
 * <p>
 * 在 Spring Boot 启动阶段自动完成以下动作：
 * <ul>
 *     <li>检查目标集合是否存在</li>
 *     <li>不存在时按当前配置创建集合与向量索引</li>
 *     <li>已存在但未加载时执行 load，确保后续检索可直接使用</li>
 * </ul>
 */
@Component
public class MilvusCollectionInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MilvusCollectionInitializer.class);

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties milvusProperties;

    public MilvusCollectionInitializer(MilvusClientV2 milvusClient, MilvusProperties milvusProperties) {
        this.milvusClient = milvusClient;
        this.milvusProperties = milvusProperties;
    }

    @Override
    public void run(String... args) {
        String collectionName = milvusProperties.getCollectionName();
        try {
            Boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());

            if (Boolean.FALSE.equals(exists)) {
                createCollection(collectionName);
                logger.info("Milvus 集合 '{}' 已创建", collectionName);
                return;
            }

            Boolean loaded = milvusClient.getLoadState(GetLoadStateReq.builder()
                    .collectionName(collectionName)
                    .build());
            if (Boolean.FALSE.equals(loaded)) {
                milvusClient.loadCollection(LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());
                logger.info("Milvus 集合 '{}' 已加载", collectionName);
            } else {
                logger.info("Milvus 集合 '{}' 已存在且已加载", collectionName);
            }
        } catch (Exception e) {
            logger.error("初始化 Milvus 集合失败: {}", collectionName, e);
            throw new RuntimeException("初始化 Milvus 集合失败", e);
        }
    }

    /**
     * 根据当前业务字段定义创建知识分块集合。
     * <p>
     * 其中 {@code vector} 是向量检索字段，其余字段主要承担检索结果展示、
     * 权限过滤和后续融合排序所需的元数据职责。
     */
    private void createCollection(String collectionName) {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();

        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .autoID(false)
                .maxLength(128)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("fileMd5")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunkId")
                .dataType(DataType.Int64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("textContent")
                .dataType(DataType.VarChar)
                .maxLength(milvusProperties.getTextMaxLength())
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("modelVersion")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("userId")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("orgTag")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("isPublic")
                .dataType(DataType.Bool)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("vector")
                .dataType(DataType.FloatVector)
                .dimension(milvusProperties.getDimension())
                .build());

        IndexParam indexParam = buildVectorIndexParam();

        CreateCollectionReq request = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(Collections.singletonList(indexParam))
                .build();

        milvusClient.createCollection(request);
    }

    /**
     * 根据配置动态构建向量索引参数。
     * 当前默认使用 HNSW + COSINE，并支持通过配置调整索引构建参数。
     */
    private IndexParam buildVectorIndexParam() {
        IndexParam.IndexType indexType = resolveIndexType(milvusProperties.getIndexType());
        IndexParam.MetricType metricType = resolveMetricType(milvusProperties.getMetricType());

        var builder = IndexParam.builder()
                .fieldName("vector")
                .indexName("idx_vector")
                .indexType(indexType)
                .metricType(metricType);

        if (indexType == IndexParam.IndexType.HNSW) {
            builder.extraParams(Map.of(
                    "M", milvusProperties.getHnswM(),
                    "efConstruction", milvusProperties.getHnswEfConstruction()
            ));
        }

        return builder.build();
    }

    /**
     * 将配置文件中的索引类型字符串安全映射为 Milvus SDK 枚举。
     */
    private IndexParam.IndexType resolveIndexType(String rawIndexType) {
        try {
            return IndexParam.IndexType.valueOf(rawIndexType.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            logger.warn("未知的 Milvus indexType='{}'，回退为 HNSW", rawIndexType);
            return IndexParam.IndexType.HNSW;
        }
    }

    /**
     * 将配置文件中的距离度量字符串安全映射为 Milvus SDK 枚举。
     */
    private IndexParam.MetricType resolveMetricType(String rawMetricType) {
        try {
            return IndexParam.MetricType.valueOf(rawMetricType.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            logger.warn("未知的 Milvus metricType='{}'，回退为 COSINE", rawMetricType);
            return IndexParam.MetricType.COSINE;
        }
    }
}
