package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.management.metric.models.MetricOverviewModel;

import java.util.List;

/**
 * PublishMetricService
 *
 * @author page
 * @date 2022/12/27
 */
public interface PublishMetricService {

    List<MetricOverviewModel> getMetrics();

}
