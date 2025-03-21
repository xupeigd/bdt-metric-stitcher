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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = TableNames.TABLE_STIT_QUOTAS,
        indexes = {
        })
@Where(clause = " status = 1 ")
public class QuotaDBVO {

    /**
     * 逻辑Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = " bigint(11) NOT NULL AUTO_INCREMENT COMMENT '逻辑主键' ")
    Integer id;

    /**
     * 源头dbvo的Id
     */
    @Column(name = "source_id", columnDefinition = " bigint(11) NOT NULL default 0 COMMENT '源头Id' ")
    Integer sourceId;

    /**
     * 关联的应用Id
     */
    @Column(name = "app_id", columnDefinition = " bigint(11) NOT NULL DEFAULT 0 COMMENT '应用Id' ")
    Integer appId;

    /**
     * 应用类型
     * <p>
     * 0内部应用 1外部应用
     */
    @Column(name = "app_type", columnDefinition = " tinyint(2) NOT NULL DEFAULT 1 COMMENT '应用类型 0内部应用 1外部应用' ")
    Integer appType;

    /**
     * 关联的指标Id
     */
    @Column(name = "metric_id", columnDefinition = " bigint(11) NOT NULL DEFAULT 0 COMMENT '指标Id' ")
    Integer metricId;


    @Column(name = "flag", columnDefinition = " varchar(64) NOT NULL DEFAULT '' COMMENT '标识' ")
    String flag;

    /**
     * 额度值
     */
    @Column(columnDefinition = "bigint(20) NOT NULL DEFAULT 0 COMMENT '额度值' ")
    Long quota;

    /**
     * 刷新cron表达式
     */
    @Column(name = "cron_express", columnDefinition = "varchar(255) NOT NULL DEFAULT '' COMMENT '刷新cron表达式' ")
    String cronExpress;

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
     * 状态
     * 0 删除 1 可用
     *
     * @see DataStatus
     */
    @Column(columnDefinition = " tinyint(2) NOT NULL DEFAULT 1 COMMENT '数据状态 0删除 1可用' ")
    @Enumerated(EnumType.ORDINAL)
    DataStatus status;

}
