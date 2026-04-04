package com.yizhaoqi.smartpai.entity;

import com.yizhaoqi.smartpai.model.DocumentVector;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与具体检索后端解耦的知识块文档模型。
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

    public static String buildId(String fileMd5, Integer chunkId) {
        return fileMd5 + "_" + chunkId;
    }

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
