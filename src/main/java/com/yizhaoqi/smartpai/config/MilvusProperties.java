package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Milvus 连接、索引与检索参数配置。
 * <p>
 * 对应 {@code application*.yml} 中的 {@code milvus.*} 配置项，
 * 既包含连接信息，也包含集合结构、索引类型和检索参数。
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

    /**
     * 解析最终连接地址。
     * <p>
     * 若显式配置了 uri，则优先使用；否则回退到 host + port 形式。
     */
    public String getResolvedUri() {
        if (StringUtils.hasText(uri)) {
            return uri;
        }
        return "http://" + host + ":" + port;
    }

    /**
     * 解析最终认证信息。
     * <p>
     * 优先使用 token；若未提供 token，则尝试将 username 和 password
     * 组装成 Milvus SDK 支持的 {@code username:password} 形式。
     */
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
