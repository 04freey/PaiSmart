package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.KnowledgeChunkDocument;
import com.yizhaoqi.smartpai.entity.VectorSearchRequest;

import java.util.List;

/**
 * 统一向量存储抽象。
 * <p>
 * 通过接口屏蔽底层向量库实现差异，便于在业务层统一完成写入、检索和删除操作，
 * 同时也为后续替换或扩展新的向量存储后端预留空间。
 */
public interface VectorStore {

    /**
     * 批量写入或更新知识块。
     */
    void upsert(List<KnowledgeChunkDocument> documents);

    /**
     * 执行一次向量检索。
     */
    List<KnowledgeChunkDocument> search(VectorSearchRequest request);

    /**
     * 按文件维度删除对应知识块。
     */
    void deleteByFileMd5(String fileMd5);

    /**
     * 清空整个向量存储中的知识块数据。
     */
    void deleteAll();
}
