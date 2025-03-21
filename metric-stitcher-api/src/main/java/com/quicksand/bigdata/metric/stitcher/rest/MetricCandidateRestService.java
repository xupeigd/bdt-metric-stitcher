package com.quicksand.bigdata.metric.stitcher.rest;

import com.quicksand.bigdata.metric.management.metric.models.CandidateValuePairModel;
import com.quicksand.bigdata.vars.http.model.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * MetricCandidateRestService
 *
 * @author page
 * @date 2022/11/17
 */
public interface MetricCandidateRestService {


    /**
     * 探测指标的纬度值
     *
     * @param keyword    英文名称/编号
     * @param dimensions 纬度名称（半角逗号分隔）
     * @return list of CandidateValuePairModel
     */
    @GetMapping("/stit/metrics/cvi")
    Response<List<CandidateValuePairModel>> candidateDimensions(@RequestParam("keyword") String keyword,
                                                                @RequestParam("dimensions") List<String> dimensions);

    /**
     * 探测指标的纬度值
     *
     * @param id         指标Id
     * @param dimensions 纬度名称（半角逗号分隔）
     * @return list of CandidateValuePairModel
     */
    @GetMapping("/stit/metrics/cvi/{id}")
    Response<List<CandidateValuePairModel>> candidateDimensions(@PathVariable("id") Integer id,
                                                                @RequestParam("dimensions") List<String> dimensions);


}
