package com.quicksand.bigdata.metric.stitcher.vos;

import com.quicksand.bigdata.metric.management.consts.DataStatus;
import lombok.Getter;

import java.util.Date;

/**
 * QuotaVO
 *
 * @author page
 * @date 2022/12/16
 */
@Getter
public class QuotaVO {

    /**
     * 逻辑Id
     */
    Integer id;

    /**
     * 源头dbvo的Id
     */
    Integer sourceId;

    /**
     * 关联的应用Id
     */
    Integer appId;

    /**
     * 应用类型
     * <p>
     * 0内部应用 1外部应用
     */
    Integer appType;

    /**
     * 关联的指标Id
     */
    Integer metricId;


    String flag;

    /**
     * 额度值
     */
    Long quota;

    /**
     * 刷新cron表达式
     */
    String cronExpress;

    /**
     * 创建时间
     */
    Date createTime;

    /**
     * 更新时间
     */
    Date updateTime;

    /**
     * 状态
     * 0 删除 1 可用
     *
     * @see DataStatus
     */
    DataStatus status;

}
