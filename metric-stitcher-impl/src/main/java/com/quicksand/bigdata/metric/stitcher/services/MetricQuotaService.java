package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.stitcher.vos.QuotaVO;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;

import javax.validation.constraints.NotNull;

/**
 * MetricQuotaService
 *
 * @author page
 * @date 2022/11/22
 */
public interface MetricQuotaService {

    /**
     * 判断配额是否超出限制
     *
     * @param metricId     指标id
     * @param appRequestVO 请求对象
     * @return 是否超出 true/false
     */
    boolean overQuotaLimit(int metricId, @NotNull AppRequestVO appRequestVO);

    /**
     * 记录额度消耗
     *
     * @param metricId     指标id
     * @param appRequestVO 请求对象
     */
    void logQuotaCost(int metricId, @NotNull AppRequestVO appRequestVO);

    /**
     * 刷新额度数据
     *
     * @param quota 额度
     */
    void refreshQuota(QuotaVO quota);

}
