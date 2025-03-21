package com.quicksand.bigdata.metric.stitcher.repos;

import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaCostDBVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * QuotaCostAutoRepo
 *
 * @author page
 * @date 2022/12/20
 */
@Repository
public interface QuotaCostAutoRepo
        extends JpaRepository<QuotaCostDBVO, Integer> {

    /**
     * 查询下次刷新时间大于给定时间的消耗记录
     *
     * @param date 给定时间
     * @return list of QuotaCostDBVO
     */
    List<QuotaCostDBVO> findAllByNextRefreshDateAfterOrderByUpdateTimeDesc(Date date);

    /**
     * 查找当前正在生效的cost记录
     *
     * @param appId    应用Id
     * @param metricId 指标Id
     * @param flag     标识
     * @param date     日期
     * @return list of QuotaCostDBVO
     */
    List<QuotaCostDBVO> findByAppIdAndMetricIdAndFlagLikeAndNextRefreshDateAfterOrderByUpdateTimeDesc(int appId, int metricId, String flag, Date date);

    /**
     * 查找当前正在生效的cost记录
     *
     * @param appId    应用Id
     * @param metricId 指标Id
     * @param flag     标识
     * @param logDate  基准时间
     * @param date     日期
     * @return list of QuotaCostDBVO
     */
    List<QuotaCostDBVO> findByAppIdAndMetricIdAndFlagLikeAndLogDateAndNextRefreshDateAfterOrderByUpdateTimeDesc(int appId, int metricId, String flag, Date logDate, Date date);

}
