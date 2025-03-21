package com.quicksand.bigdata.metric.stitcher.services;

import java.util.Date;
import java.util.List;

/**
 * MetricQpsService
 *
 * @author page
 * @date 2022/12/22
 */
public interface MetricQpsService {


    /**
     * 瞬间的OPS记录
     */
    String RK_METRIC_QPS = "STIT:TQPS:%d:%d";

    /**
     * 调用时序记录
     * <p>
     * struct： zset
     * expired 72H
     * key {0} yyyyMMdd格式
     * value: [CQ]:%d:%d:%d:%d {0} appId {1} metricId {2} costMills {3} logDate(yyyyMMddHHssmm)
     * score: mills
     */
    String RK_INVOKE_TLS = "STIT:INV:TLS:%s";

    /**
     * +1
     *
     * @param appId     应用Id
     * @param metricIds 指标Ids
     */
    void incr(int appId, List<Integer> metricIds);

    /**
     * -1
     *
     * @param appId     应用Id
     * @param metricIds 指标Ids
     * @param candidate
     * @param costMills
     */
    void reduce(int appId, List<Integer> metricIds, boolean candidate, long costMills);

    /**
     * 查询当日的配额消耗
     *
     * @param appId    应用Id
     * @param metricId 指标Id
     * @param date
     * @return long
     */
    long curDaycost(int appId, int metricId, Date date);

}
