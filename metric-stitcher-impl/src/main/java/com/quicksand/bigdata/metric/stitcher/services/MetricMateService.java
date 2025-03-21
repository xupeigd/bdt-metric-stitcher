package com.quicksand.bigdata.metric.stitcher.services;

/**
 * MetricMateService
 *
 * @author page
 * @date 2023/2/16
 */
public interface MetricMateService {

    /**
     * 指标对应的Id是否存在且为上线状态
     *
     * @param metricId 指标id
     * @return true/false
     */
    boolean existMetric(int metricId);

    /**
     * 根据keyword(英文名/编号)查找指标Id
     *
     * @param keyword(英文名/编号)
     * @return id of metric
     */
    Integer findMetricId(String keyword);

}
