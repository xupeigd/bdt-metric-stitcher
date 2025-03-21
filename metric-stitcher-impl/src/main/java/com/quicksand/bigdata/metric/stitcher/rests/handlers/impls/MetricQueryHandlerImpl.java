package com.quicksand.bigdata.metric.stitcher.rests.handlers.impls;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.apis.models.ExplainGroupModel;
import com.quicksand.bigdata.metric.management.datasource.models.ClusterInfoModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricCatculateResponseModel;
import com.quicksand.bigdata.metric.management.metric.models.ResultSetModel;
import com.quicksand.bigdata.metric.stitcher.rest.MetricQueryRestService;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.AbstractDispatchedHandler;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.MetricQueryHandler;
import com.quicksand.bigdata.metric.stitcher.services.EngineLAService;
import com.quicksand.bigdata.metric.stitcher.services.ExplainLAService;
import com.quicksand.bigdata.query.consts.JobState;
import com.quicksand.bigdata.query.models.QueryRespModel;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import com.quicksand.bigdata.vars.util.HyperAttributes;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MetricHandler
 *
 * @author page
 * @date 2022/11/14
 */
@Slf4j
@Configuration
public class MetricQueryHandlerImpl
        extends AbstractDispatchedHandler
        implements MetricQueryHandler {

    @Bean
    MetricQueryHandler metricQueryHandler() {
        return new MetricQueryHandlerImpl()
                .scan(MetricQueryRestService.class);
    }

    @Bean
    @DependsOn("metricQueryHandler")
    RouterFunction<ServerResponse> routes(MetricQueryHandler metricQueryHandler) {
        return super.routes((AbstractDispatchedHandler) metricQueryHandler);
    }

    private Response<MetricCatculateResponseModel> queryMetricFunction(Integer id, ExplainAttributesModel attributes) {
        StopWatch queryWatch = new StopWatch();
        queryWatch.start("-- 编译sql");
        //先解析
        ExplainGroupModel explainResult = explainLAService.explainMetric(id, attributes);
        if (null == explainResult
                || null == explainResult.getDatasetId()
                || 0 >= explainResult.getDatasetId()
                || null == explainResult.getClusterId()
                || 0 >= explainResult.getClusterId()) {
            Response<?> cacheResp = HyperAttributes.get(ExplainLAService.TMP_STORE_KEY, Response.class);
            if (null == cacheResp) {
                return Response.response(HttpStatus.NOT_ACCEPTABLE, "指标无法被解析，请查证！");
            } else {
                //noinspection CastCanBeRemovedNarrowingVariableType,unchecked
                return (Response<MetricCatculateResponseModel>) cacheResp;
            }
        }
        queryWatch.stop();
        queryWatch.start("-- 获取执行集群");
        ClusterInfoModel clusterInfo = clusterLAService.findClusterInfo(explainResult.getClusterId());
        if (null == clusterInfo) {
            return Response.response(HttpStatus.NOT_ACCEPTABLE, "指标异常：数据集群不存在！");
        }
        queryWatch.stop();
        queryWatch.start("-- QE查询");
        ResultSetModel resultSetModel = engineLAService.commonQuery(EngineLAService.ClusterInfo.from(clusterInfo), explainResult.getSql(), new EngineLAService.Mapper<ResultSetModel>() {
            @Override
            public ResultSetModel applySuccess(QueryRespModel r) {
                return ResultSetModel.builder()
                        .columnMetas(EngineLAService.Mapper.coverColumnMetas(r))
                        .state(r.getState().getCode())
                        .msg(r.getResultSet().getMsg())
                        .resultMode(r.getReq().getResultMode().getCode())
                        .columns(r.getResultSet().getColumns())
                        .rows(r.getResultSet().getRows())
                        .build();
            }

            @Override
            public ResultSetModel applyDefault(QueryRespModel r) {
                return ResultSetModel.builder()
                        .state(r.getState().getCode())
                        .msg(r.getResultSet().getMsg())
                        .build();
            }
        });
        queryWatch.stop();
        log.info("queryMetricFunction done ! metricId:" + id + " timeLine>>> ["
                + StringUtils.collectionToDelimitedString(Arrays.stream(queryWatch.getTaskInfo())
                .map(v -> "TN:" + v.getTaskName() + " " + v.getTimeMillis() + "ms")
                .collect(Collectors.toList()), ",") + "]");
        boolean success = Objects.equals(JobState.Success.getCode(), resultSetModel.getState());
        MetricCatculateResponseModel mrm = MetricCatculateResponseModel.builder()
                .id(id)
                .sql(explainResult.getSql().replaceAll("\\n", ""))
                .resultSet(resultSetModel)
                .build();
        return success ? Response.ok(mrm) : Response.response(mrm, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public Response<MetricCatculateResponseModel> queryMetric(ExplainAttributesModel attributes) {
        AppRequestVO appReq = usringDebuggingSign(fetchFromThreadLocal());
        if (null == appReq) {
            return Response.response(HttpStatus.UNAUTHORIZED);
        }
        String keyword = null != attributes && !CollectionUtils.isEmpty(attributes.getMetrics()) && StringUtils.hasText(attributes.getMetrics().get(0)) ? attributes.getMetrics().get(0) : "";
        if (Objects.equals("", keyword)) {
            return Response.response(HttpStatus.BAD_REQUEST, "缺少指标信息！");
        }
        Integer metricId = metricMetaService.findMetricId(keyword);
        if (null == metricId || 0 >= metricId) {
            return Response.response(HttpStatus.BAD_REQUEST, "指标不存在/不支持调用，请查证！");
        }
        attributes.setMetricIds(Collections.singletonList(metricId));
        return queryMetric(metricId, attributes);
    }

    @Override
    public Response<MetricCatculateResponseModel> queryMetric(Integer id, ExplainAttributesModel attributes) {
        long startMills = System.currentTimeMillis();
        AppRequestVO appReq = usringDebuggingSign(fetchFromThreadLocal());
        if (null == appReq) {
            return Response.response(HttpStatus.UNAUTHORIZED);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("-- 校验配额 metricId:" + id);
        try {
            if (!metricMetaService.existMetric(id)) {
                return Response.response(HttpStatus.BAD_REQUEST, "指标不存在/不支持调用，请查证！");
            }
            //严格限制该值
            attributes.setMetricIds(Collections.singletonList(id));
            metricQpsService.incr(appReq.getId(), Lists.newArrayList(id));
            //检查配额
            if (metricQuotaService.overQuotaLimit(id, appReq)) {
                stopWatch.stop();
                log.info("quota over limit :candidateDimensions ! appId:{}`metricId:{}", appReq.getId(), id);
                return Response.response(HttpStatus.PAYMENT_REQUIRED, "指标未授权/额度已耗尽，请查证！");
            }
            stopWatch.stop();
            stopWatch.start("-- 指标数据查询 metricId:" + id);
            Response<MetricCatculateResponseModel> response = Try.of(() -> queryMetricFunction(id, attributes)).get();
            stopWatch.stop();
            //记录配额消耗（200即属正常调用）
            if (Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode())) {
                stopWatch.start("-- log quota metricId:" + id);
                metricQuotaService.logQuotaCost(id, appReq);
                stopWatch.stop();
            }
            log.info("queryMetric done ! metricId:" + id + " timeLine>>> ["
                    + StringUtils.collectionToDelimitedString(Arrays.stream(stopWatch.getTaskInfo())
                    .map(v -> "TN:" + v.getTaskName() + " " + v.getTimeMillis() + "ms")
                    .collect(Collectors.toList()), ",") + "]");
            return response;
        } finally {
            metricQpsService.reduce(appReq.getId(), Lists.newArrayList(id), false, System.currentTimeMillis() - startMills);
        }
    }

}
