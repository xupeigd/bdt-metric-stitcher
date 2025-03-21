package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.apis.models.ExplainGroupModel;

/**
 * ExplainService
 *
 * @author page
 * @date 2022/11/16
 */
public interface ExplainLAService {

    String TMP_STORE_KEY = "EXP_RESPONSE";

    /**
     * 编译指标
     *
     * @param metricId   指标id
     * @param attributes 编译属性
     * @return instance of ExplainGroupModel
     */
    ExplainGroupModel explainMetric(int metricId, ExplainAttributesModel attributes);


}
