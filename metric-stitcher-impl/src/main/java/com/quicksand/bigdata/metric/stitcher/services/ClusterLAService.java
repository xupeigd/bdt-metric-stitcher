package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.management.datasource.models.ClusterInfoModel;

/**
 * @author page
 * @date 2022/11/16
 */
public interface ClusterLAService {

    /**
     * 获取集群连接信息
     *
     * @param clusterId 集群id
     * @return
     */
    ClusterInfoModel findClusterInfo(int clusterId);

}
