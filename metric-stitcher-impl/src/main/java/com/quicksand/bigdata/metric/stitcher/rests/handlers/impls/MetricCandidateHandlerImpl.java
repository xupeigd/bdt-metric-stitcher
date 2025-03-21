package com.quicksand.bigdata.metric.stitcher.rests.handlers.impls;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.apis.models.ExplainGroupModel;
import com.quicksand.bigdata.metric.management.datasource.models.ClusterInfoModel;
import com.quicksand.bigdata.metric.management.metric.models.CandidateValuePairModel;
import com.quicksand.bigdata.metric.stitcher.rest.MetricCandidateRestService;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.AbstractDispatchedHandler;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.MetricCandidateHandler;
import com.quicksand.bigdata.metric.stitcher.services.EngineLAService;
import com.quicksand.bigdata.metric.stitcher.services.ExplainLAService;
import com.quicksand.bigdata.query.consts.ResultMode;
import com.quicksand.bigdata.query.models.QueryRespModel;
import com.quicksand.bigdata.query.models.SqlColumnMetaModel;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import com.quicksand.bigdata.vars.util.HyperAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MetricCandidateHandlerImpl
 *
 * @author page
 * @date 2022/11/17
 */
@Slf4j
@Configuration
public class MetricCandidateHandlerImpl
        extends AbstractDispatchedHandler
        implements MetricCandidateHandler {

    @Bean
    MetricCandidateHandler metricCandidateHandler() {
        return new MetricCandidateHandlerImpl()
                .scan(MetricCandidateRestService.class);
    }

    @Bean
    @DependsOn("metricCandidateHandler")
    RouterFunction<ServerResponse> metricCandidateRoutes(MetricCandidateHandler metricCandidateHandler) {
        return super.routes((AbstractDispatchedHandler) metricCandidateHandler);
    }

    private Response<List<CandidateValuePairModel>> candidateDimensionsFunction(Integer id, List<String> dimensions) {
        ExplainAttributesModel explainAttributes = ExplainAttributesModel.builder()
                .metricIds(Lists.newArrayList(id))
                .dimensions(dimensions)
                .build();
        ExplainGroupModel explainResult = explainLAService.explainMetric(id, explainAttributes);
        if (null == explainResult
                || null == explainResult.getDatasetId()
                || 0 >= explainResult.getDatasetId()
                || null == explainResult.getClusterId()
                || 0 >= explainResult.getClusterId()) {
            Response<?> cacheResp = HyperAttributes.get(ExplainLAService.TMP_STORE_KEY, Response.class);
            if (null == cacheResp) {
                return Response.response(HttpStatus.NOT_ACCEPTABLE, "指标无法被解析，请查证！");
            } else {
                //noinspection unchecked,CastCanBeRemovedNarrowingVariableType
                return (Response<List<CandidateValuePairModel>>) cacheResp;
            }
        }

        ClusterInfoModel clusterInfo = clusterLAService.findClusterInfo(explainResult.getClusterId());
        if (null == clusterInfo) {
            return Response.response(HttpStatus.NOT_ACCEPTABLE, "指标异常：数据集群不存在！");
        }
        String limitSql = String.format("%s limit 1000", explainResult.getSql());
        List<CandidateValuePairModel> candidateValuePairs = engineLAService.commonQuery(EngineLAService.ClusterInfo.from(clusterInfo),
                limitSql, new EngineLAService.Mapper<List<CandidateValuePairModel>>() {
                    @Override
                    public List<CandidateValuePairModel> applySuccess(QueryRespModel queryRespModel) {
                        Map<String, CandidateValuePairModel> valuse = new HashMap<>();
                        Map<Integer, SqlColumnMetaModel> columnMetas = queryRespModel.getResultSet().getColumnMetas().stream()
                                .filter(v -> !explainResult.getMetricsNames().contains(v.getName()))
                                .collect(Collectors.toMap(SqlColumnMetaModel::getIndex, Function.identity()));
                        if (Objects.equals(ResultMode.Column, queryRespModel.getReq().getResultMode())) {
                            for (int i = 0; i < queryRespModel.getResultSet().getColumns().size(); i++) {
                                SqlColumnMetaModel sqlColumnMetaModel = columnMetas.get(i);
                                if (null != sqlColumnMetaModel) {
                                    valuse.put(sqlColumnMetaModel.getName(), CandidateValuePairModel.builder()
                                            .name(sqlColumnMetaModel.getName())
                                            .values(new ArrayList<>(queryRespModel.getResultSet().getColumns()
                                                    .get(i)
                                                    .stream()
                                                    .map(String::valueOf)
                                                    .collect(Collectors.toSet())))
                                            .build());
                                }
                            }
                        } else {
                            columnMetas.forEach((k, v) -> {
                                CandidateValuePairModel cpv = valuse.getOrDefault(v.getName(), CandidateValuePairModel.builder()
                                        .name(v.getName())
                                        .values(new ArrayList<>())
                                        .build());
                                valuse.put(cpv.getName(), cpv);
                                for (List<?> row : queryRespModel.getResultSet().getRows()) {
                                    String ret = String.valueOf(row.get(v.getIndex()));
                                    if (!cpv.getValues().contains(ret)) {
                                        cpv.getValues().add(ret);
                                    }
                                }
                            });
                        }
                        return new ArrayList<>(valuse.values());
                    }
                });
        if (null == candidateValuePairs) {
            return Response.response(HttpStatus.EXPECTATION_FAILED, "探测失败，请稍后重试！");
        }
        return Response.ok(candidateValuePairs);
    }

    @Operation(method = "探查指标")
    @Override
    public Response<List<CandidateValuePairModel>> candidateDimensions(String keyword, List<String> dimensions) {
        AppRequestVO appReq = usringDebuggingSign(fetchFromThreadLocal());
        if (null == appReq) {
            return Response.response(HttpStatus.UNAUTHORIZED);
        }
        Integer metricId = metricMetaService.findMetricId(keyword);
        if (null == metricId || 0 >= metricId) {
            return Response.response(HttpStatus.BAD_REQUEST, "指标不存在/不支持探查 ！");
        }
        return candidateDimensions(metricId, dimensions);
    }

    @Operation(method = "探查指标")
    @Override
    public Response<List<CandidateValuePairModel>> candidateDimensions(Integer id, List<String> dimensions) {
        long startMills = System.currentTimeMillis();
        AppRequestVO appReq = usringDebuggingSign(fetchFromThreadLocal());
        if (null == appReq) {
            return Response.response(HttpStatus.UNAUTHORIZED);
        }
        try {
            boolean metricEnable = metricMetaService.existMetric(id);
            if (!metricEnable) {
                return Response.response(HttpStatus.BAD_REQUEST, "指标不存在/不支持探查 ！");
            }
            metricQpsService.incr(appReq.getId(), Lists.newArrayList(id));
            //检查配额
            if (metricQuotaService.overQuotaLimit(id, appReq)) {
                log.info("quota over limit :candidateDimensions ! appId:{}`metricId:{}", appReq.getId(), id);
                return Response.response(HttpStatus.PAYMENT_REQUIRED, "指标未授权/额度已耗尽，请查证！");
            }
            Response<List<CandidateValuePairModel>> response = Try.of(() -> candidateDimensionsFunction(id, dimensions)).get();
            //记录配额消耗（200即属正常调用）
            if (Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode())) {
                metricQuotaService.logQuotaCost(id, appReq);
            }
            return response;
        } finally {
            metricQpsService.reduce(appReq.getId(), Lists.newArrayList(id), true, System.currentTimeMillis() - startMills);
        }
    }

}
