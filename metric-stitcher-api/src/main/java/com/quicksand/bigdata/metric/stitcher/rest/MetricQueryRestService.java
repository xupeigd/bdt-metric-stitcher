package com.quicksand.bigdata.metric.stitcher.rest;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricCatculateResponseModel;
import com.quicksand.bigdata.vars.http.model.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * MetricQueryRestService
 *
 * @author page
 * @date 2022/11/14
 */
public interface MetricQueryRestService {


    /**
     * 查询指标数据
     *
     * @param attributes 附加属性
     * @return insatnce of MetricCatculateResponseModel
     */
    @PostMapping("/stit/metrics/rqi")
    Response<MetricCatculateResponseModel> queryMetric(@RequestBody ExplainAttributesModel attributes);

    /**
     * 查询指标数据
     *
     * @param id         指标Id
     * @param attributes 附加属性
     * @return insatnce of MetricCatculateResponseModel
     */
    @PostMapping("/stit/metrics/rqi/{id}")
    Response<MetricCatculateResponseModel> queryMetric(@PathVariable("id") Integer id,
                                                       @RequestBody ExplainAttributesModel attributes);

}
