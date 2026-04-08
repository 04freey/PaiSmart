package com.yizhaoqi.smartpai.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Milvus 客户端配置类。
 * <p>
 * 负责把项目中的 {@link MilvusProperties} 转换为 Milvus SDK 可识别的连接参数，
 * 并注册为单例客户端 Bean，供向量写入、检索和集合初始化逻辑复用。
 */
@Configuration
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(MilvusProperties milvusProperties) {
        var builder = ConnectConfig.builder()
                .uri(milvusProperties.getResolvedUri());

        String token = milvusProperties.getResolvedToken();
        if (StringUtils.hasText(token)) {
            // 兼容 token 或 username:password 两种认证拼装方式。
            builder.token(token);
        }

        return new MilvusClientV2(builder.build());
    }
}
