package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 混合检索参数配置。
 * <p>
 * 该配置用于控制向量召回与文本召回在融合排序阶段的权重，以及两路召回各自的候选集规模。
 */
@Component
@ConfigurationProperties(prefix = "search")
@Data
public class SearchProperties {

    /**
     * 向量检索分值权重。
     */
    private double vectorWeight = 0.7;

    /**
     * 文本检索分值权重。
     */
    private double textWeight = 0.3;

    /**
     * 向量召回候选数量。
     */
    private int vectorRecallK = 40;

    /**
     * 文本召回候选数量。
     */
    private int textRecallK = 40;
}
