package com.yizhaoqi.smartpai.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Milvus 客户端配置。
 */
@Configuration
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(MilvusProperties milvusProperties) {
        var builder = ConnectConfig.builder()
                .uri(milvusProperties.getResolvedUri());

        String token = milvusProperties.getResolvedToken();
        if (StringUtils.hasText(token)) {
            builder.token(token);
        }

        return new MilvusClientV2(builder.build());
    }
}
