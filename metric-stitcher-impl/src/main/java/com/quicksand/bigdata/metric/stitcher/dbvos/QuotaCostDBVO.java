package com.quicksand.bigdata.metric.stitcher.dbvos;

import com.quicksand.bigdata.metric.management.consts.DataStatus;
import com.quicksand.bigdata.metric.stitcher.consts.TableNames;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.util.Date;

/**
 * QuotaCostDBVO
 *
 * @author page
 * @date 2022/12/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = TableNames.TABLE_STIT_QUOTA_COSTS,
        indexes = {
                @Index(name = "idx_app_metric_flag_next_refresh_date", columnList = "app_id,metric_id,flag,next_refresh_date")
        })
@Where(clause = " status = 1 ")
public class QuotaCostDBVO {

    /**
     * 逻辑Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = " bigint(11) NOT NULL AUTO_INCREMENT COMMENT '逻辑主键' ")
    Integer id;

    /**
     * 关联的应用Id
     */
    @Column(name = "app_id", columnDefinition = " bigint(11) NOT NULL DEFAULT 0 COMMENT '应用Id' ")
    Integer appId;

    /**
     * 关联的指标Id
     */
    @Column(name = "metric_id", columnDefinition = " bigint(11) NOT NULL DEFAULT 0 COMMENT '指标Id' ")
    Integer metricId;

    /**
     * 配额的刷新flag
     * (不作为依据)
     */
    @Column(name = "flag", columnDefinition = " varchar(64) NOT NULL DEFAULT '' COMMENT '标识' ")
    String flag;

    /**
     * 下次刷新标识
     */
    @Column(name = "next_refresh_date", columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次刷新时间' ")
    Date nextRefreshDate;

    /**
     * 当前的消耗额度值
     */
    @Column(name = "cur_cost", columnDefinition = "bigint(20) NOT NULL DEFAULT 0 COMMENT '当前的消耗额度值' ")
    Long curCost;

    /**
     * 日计配额消耗值
     */
    @Column(name = "day_cost", columnDefinition = "bigint(20) NOT NULL DEFAULT 0 COMMENT '日计配额消耗' ")
    Long dayCost;

    /**
     * 周期内最大的qps
     */
    @Column(name = "max_qps", columnDefinition = "bigint(11) NOT NULL DEFAULT 0 COMMENT '周期内最大的QPS' ")
    Integer maxQps;

    /**
     * 创建时间
     */
    @Column(name = "create_time", columnDefinition = " datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' ")
    Date createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", columnDefinition = " datetime  NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间' ")
    Date updateTime;

    /**
     * 记录时间
     * <p>
     * （当天的23:59:59基准）
     */
    @Column(name = "log_date", columnDefinition = " datetime  NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间' ")
    Date logDate;

    /**
     * 状态
     * 0 删除 1 可用
     *
     * @see DataStatus
     */
    @Column(columnDefinition = " tinyint(2) NOT NULL DEFAULT 1 COMMENT '数据状态 0删除 1可用' ")
    @Enumerated(EnumType.ORDINAL)
    DataStatus status;

}
