package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 混合检索相关参数。
 */
@Component
@ConfigurationProperties(prefix = "search")
@Data
public class SearchProperties {

    private double vectorWeight = 0.7;
    private double textWeight = 0.3;
    private int vectorRecallK = 40;
    private int textRecallK = 40;
}
