package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.KnowledgeChunkDocument;
import com.yizhaoqi.smartpai.entity.VectorSearchRequest;

import java.util.List;

/**
 * 统一向量存储抽象。
 */
public interface VectorStore {

    void upsert(List<KnowledgeChunkDocument> documents);

    List<KnowledgeChunkDocument> search(VectorSearchRequest request);

    void deleteByFileMd5(String fileMd5);

    void deleteAll();
}
