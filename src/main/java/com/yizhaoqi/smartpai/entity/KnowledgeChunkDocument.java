package com.yizhaoqi.smartpai.entity;

import com.yizhaoqi.smartpai.model.DocumentVector;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与具体检索后端解耦的知识块模型。
 * <p>
 * 该对象作为应用层统一的数据载体，用于在 MySQL、Milvus 和业务服务之间传递
 * 文本块内容、权限信息、向量数据以及检索分值，避免上层逻辑直接依赖底层存储实现。
 */
@Data
@NoArgsConstructor
public class KnowledgeChunkDocument {

    private String id;
    private String fileMd5;
    private Integer chunkId;
    private String textContent;
    private float[] vector;
    private String modelVersion;
    private String userId;
    private String orgTag;
    private boolean isPublic;
    private Float score;

    public KnowledgeChunkDocument(String id, String fileMd5, Integer chunkId, String textContent,
                                  float[] vector, String modelVersion, String userId,
                                  String orgTag, boolean isPublic) {
        this.id = id;
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = textContent;
        this.vector = vector;
        this.modelVersion = modelVersion;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
    }

    /**
     * 统一构造知识块主键，保证 MySQL 与 Milvus 两侧都能稳定定位到同一个块。
     */
    public static String buildId(String fileMd5, Integer chunkId) {
        return fileMd5 + "_" + chunkId;
    }

    /**
     * 将原有的数据库实体转换为检索层统一模型。
     * <p>
     * 这里不主动携带向量内容，避免在非必要路径上增加内存和序列化开销。
     */
    public static KnowledgeChunkDocument fromDocumentVector(DocumentVector documentVector) {
        return new KnowledgeChunkDocument(
                buildId(documentVector.getFileMd5(), documentVector.getChunkId()),
                documentVector.getFileMd5(),
                documentVector.getChunkId(),
                documentVector.getTextContent(),
                null,
                documentVector.getModelVersion(),
                documentVector.getUserId(),
                documentVector.getOrgTag(),
                documentVector.isPublic()
        );
    }
}
