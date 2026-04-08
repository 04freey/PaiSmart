package com.yizhaoqi.smartpai.entity;

import java.util.List;

/**
 * 统一的向量检索请求模型。
 * <p>
 * 用于将查询向量、召回数量以及访问权限信息一次性传入底层向量存储实现，
 * 让上层业务代码无需感知不同向量库的参数差异。
 */
public record VectorSearchRequest(
        float[] queryVector,
        int topK,
        String userId,
        List<String> orgTags,
        boolean publicOnly
) {
}
