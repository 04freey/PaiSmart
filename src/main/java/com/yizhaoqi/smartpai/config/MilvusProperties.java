package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Milvus 连接与集合配置。
 */
@Component
@ConfigurationProperties(prefix = "milvus")
@Data
public class MilvusProperties {

    /**
     * 兼容 host/port 形式配置。
     */
    private String host = "localhost";
    private int port = 19530;

    /**
     * 可直接覆盖完整 URI，例如 http://localhost:19530。
     */
    private String uri;

    /**
     * 优先使用 token；若为空则尝试 username:password。
     */
    private String token;
    private String username;
    private String password;

    private String collectionName = "knowledge_base";
    private int dimension = 2048;
    private String metricType = "COSINE";
    private String indexType = "HNSW";
    private int hnswM = 16;
    private int hnswEfConstruction = 200;
    private int searchEf = 64;
    private int textMaxLength = 4096;

    public String getResolvedUri() {
        if (StringUtils.hasText(uri)) {
            return uri;
        }
        return "http://" + host + ":" + port;
    }

    public String getResolvedToken() {
        if (StringUtils.hasText(token)) {
            return token;
        }
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            return username + ":" + password;
        }
        return null;
    }
}
