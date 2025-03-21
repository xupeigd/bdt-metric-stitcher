package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.management.consts.PubsubStatus;
import com.quicksand.bigdata.metric.management.metric.models.MetricOverviewModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricQueryModel;
import com.quicksand.bigdata.metric.management.metric.rests.MetricManageRestService;
import com.quicksand.bigdata.metric.stitcher.services.PublishMetricService;
import com.quicksand.bigdata.vars.http.JstAppInfo;
import com.quicksand.bigdata.vars.http.JstInfo;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.JsonUtils;
import com.quicksand.bigdata.vars.util.PageImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PublishMetricServiceImpl
 *
 * @author page
 * @date 2022/12/27
 */
@Slf4j
@Service
public class PublishMetricServiceImpl
        extends StitcherSecService
        implements PublishMetricService {

    @Resource
    MetricManageRestService metricManageRestService;

    public List<MetricOverviewModel> getMetrics() {
        Response<PageImpl<MetricOverviewModel>> remoteResponse = FeignDumplingsResponseRunner.of(() -> {
            JstInfo.make("");
            JstAppInfo.make(buildStitcherSignHeader());
            return metricManageRestService.listMetrics(MetricQueryModel.builder()
                    .pubsub(PubsubStatus.Online.getCode())
                    .pageNo(1)
                    .pageSize(Integer.MAX_VALUE)
                    .build());
        }, log);
        if (null != remoteResponse
                && Objects.equals(String.valueOf(HttpStatus.OK.value()), remoteResponse.getCode())
                && null != remoteResponse.getData()) {
            return remoteResponse.getData().getItems();
        } else {
            log.warn("getMetrics fail ! resp:{}", JsonUtils.toJsonString(remoteResponse));
        }
        return new ArrayList<>();
    }

}
