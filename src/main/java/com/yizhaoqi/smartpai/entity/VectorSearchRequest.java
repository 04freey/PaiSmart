package com.yizhaoqi.smartpai.entity;

import java.util.List;

/**
 * 统一的向量检索请求模型。
 */
public record VectorSearchRequest(
        float[] queryVector,
        int topK,
        String userId,
        List<String> orgTags,
        boolean publicOnly
) {
}
