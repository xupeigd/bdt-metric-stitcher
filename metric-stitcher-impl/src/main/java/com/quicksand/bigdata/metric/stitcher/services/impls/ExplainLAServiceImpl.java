package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.apis.models.ExplainGroupModel;
import com.quicksand.bigdata.metric.management.apis.rest.InvokePlatformRestService;
import com.quicksand.bigdata.metric.stitcher.services.ExplainLAService;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.HyperAttributes;
import com.quicksand.bigdata.vars.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * ExplainServiceImpl
 *
 * @author page
 * @date 2022/11/16
 */
@Slf4j
@Service
public class ExplainLAServiceImpl
        implements ExplainLAService {

    @Resource
    InvokePlatformRestService invokePlatformRestService;

    private ExplainGroupModel remoteExplain(int metricId, ExplainAttributesModel attributes) {
        log.info("---> start remoteExplain ! metridId:{},attributes:{}", metricId, JsonUtils.toJsonString(attributes));
        long startMills = System.currentTimeMillis();
        ExplainGroupModel curExplain = null;
        Response<List<ExplainGroupModel>> remoteResponse = FeignDumplingsResponseRunner.of(() -> invokePlatformRestService.explainMetrics(attributes), log);
        if (null != remoteResponse
                && Objects.equals(String.valueOf(HttpStatus.OK.value()), remoteResponse.getCode())
                && !CollectionUtils.isEmpty(remoteResponse.getData())) {
            curExplain = remoteResponse.getData().get(0);
        } else {
            log.warn("remoteExplain fail ! resp:{}", remoteResponse);
        }
        if (null == curExplain) {
            HyperAttributes.put(TMP_STORE_KEY, remoteResponse, Response.class);
            log.info("---> end remoteExplain without result ! metridId:{},attributes:{},cost:{}", metricId, JsonUtils.toJsonString(attributes), System.currentTimeMillis() - startMills);
        } else {
            log.info("---> end remoteExplain : explain complete  ! metridId:{},attributes:{},cost:{}", metricId, JsonUtils.toJsonString(attributes), System.currentTimeMillis() - startMills);
        }
        return curExplain;
    }

    @Override
    public ExplainGroupModel explainMetric(int metricId, ExplainAttributesModel attributes) {
        return remoteExplain(metricId, attributes);
    }

}
