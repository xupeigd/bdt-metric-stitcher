SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- v 1.1.1
-- ----------------------------

-- ----------------------------
-- Table struct for t_stit_quotas
-- ----------------------------

CREATE TABLE `t_stit_quotas`
(
    `id`           bigint(11) NOT NULL AUTO_INCREMENT COMMENT '逻辑主键',
    `source_id`    bigint(11) NOT NULL default 0 COMMENT '源头Id',
    `app_id`       bigint(11) NOT NULL DEFAULT 0 COMMENT '应用Id',
    `app_type`     tinyint(2) NOT NULL DEFAULt 1 COMMENT '应用类型 0内部应用 1外部应用',
    `metric_id`    bigint(11) NOT NULL DEFAULT 0 COMMENT '指标Id',
    `flag`         varchar(64)  NOT NULL DEFAULT '' COMMENT '标识',
    `quota`        bigint(20) NOT NULL DEFAULT 0 COMMENT '额度值',
    `cron_express` varchar(255) NOT NULL DEFAULT '' COMMENT '刷新cron表达式',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `status`       tinyint      NOT NULL DEFAULT '1' COMMENT '数据状态 0删除 1 可用',
    PRIMARY KEY (`id`),
    KEY            `indx_app_metric_flag`(`app_id`,`metric_id`,`flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT '额度数据表';

-- ----------------------------
-- Table struct for t_stit_quota_costs
-- ----------------------------

CREATE TABLE `t_stit_quota_costs`
(
    `id`                bigint(11) NOT NULL AUTO_INCREMENT COMMENT '逻辑主键',
    `app_id`            bigint(11) NOT NULL DEFAULT 0 COMMENT '应用Id',
    `metric_id`         bigint(11) NOT NULL DEFAULT 0 COMMENT '指标Id',
    `flag`              varchar(64) NOT NULL DEFAULT '' COMMENT '标识',
    `next_refresh_date` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次刷新时间',
    `cur_cost`          bigint(20) NOT NULL DEFAULT 0 COMMENT '当前的消耗额度值',
    `day_cost`          bigint(20) NOT NULL DEFAULT 0 COMMENT '日计配额消耗',
    `max_qps`           bigint(11) NOT NULL DEFAULT 0 COMMENT '周期内最大的QPS',
    `log_date`          datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '基准时间',
    `create_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `status`            tinyint     NOT NULL DEFAULT '1' COMMENT '数据状态 0删除 1 可用',
    PRIMARY KEY (`id`),
    KEY                 `indx_app_metric_flag`(`app_id`,`metric_id`,`flag`,`next_refresh_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT '配额数据消耗';

SET
FOREIGN_KEY_CHECKS = 1;