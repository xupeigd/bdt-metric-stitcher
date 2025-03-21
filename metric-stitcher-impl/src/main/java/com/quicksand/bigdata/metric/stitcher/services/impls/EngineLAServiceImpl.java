package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.stitcher.services.EngineLAService;
import com.quicksand.bigdata.query.consts.DsType;
import com.quicksand.bigdata.query.consts.JobState;
import com.quicksand.bigdata.query.consts.QueryMode;
import com.quicksand.bigdata.query.consts.ResultMode;
import com.quicksand.bigdata.query.models.ConnectionInfoModel;
import com.quicksand.bigdata.query.models.QueryReqModel;
import com.quicksand.bigdata.query.models.QueryRespModel;
import com.quicksand.bigdata.query.rests.QueryRestService;
import com.quicksand.bigdata.vars.concurrents.TraceFuture;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.JsonUtils;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * EngineServiceImpl
 *
 * @author page
 * @date 2022/11/15
 */
@Slf4j
@Service
public class EngineLAServiceImpl
        implements EngineLAService {

    @Value("${bdt.management.engine.result.mode:0}")
    int resultMode;
    @Value("${bdt.management.engine.request.mode:0}")
    int requestMode;

    @Resource
    QueryRestService queryRestService;

    private static boolean isEndPoint(QueryRespModel resp) {
        return JobState.Cancel.getCode() <= resp.getState().getCode();
    }

    @SuppressWarnings("UnusedReturnValue")
    private QueryReqModel supplyCommonProperties(QueryReqModel queryReqModel) {
        queryReqModel.setResultMode(1 == resultMode ? ResultMode.Row : ResultMode.Column);
        queryReqModel.setMode(0 == requestMode ? QueryMode.Async : QueryMode.Sync);
        queryReqModel.setSyncMills(60 * 1000L);
        queryReqModel.setAsyncMills(60 * 1000L);
        return queryReqModel;
    }

    private QueryRespModel queryAndWait(QueryReqModel queryReq) throws Exception {
        long startMills = System.currentTimeMillis();
        TraceFuture.run(() -> log.info("EngineService queryAndWait start ! request:{}`", JsonUtils.toJsonString(queryReq)));
        Response<QueryRespModel> queryResp = FeignDumplingsResponseRunner.of(() -> queryRestService.query(queryReq), log);
        if (!Objects.equals(String.valueOf(HttpStatus.OK.value()), queryResp.getCode())) {
            log.warn("queryAndWait fail ! queryReq:{},resp:{}", JsonUtils.toJsonString(queryReq), JsonUtils.toJsonString(queryResp));
            throw new Exception("queryRestService not aviable !");
        }
        if (Objects.equals(QueryMode.Sync, queryReq.getMode())) {
            TraceFuture.run(() -> log.info("EngineService queryAndWait complete (sync) ! cost:{}`", System.currentTimeMillis() - startMills));
            return queryResp.getData();
        } else {
            int maxReq = 10;
            Long asyncMills = queryReq.getAsyncMills();
            QueryRespModel ret = null;
            if (10 * 1000L > asyncMills) {
                Try.run(() -> TimeUnit.MILLISECONDS.sleep(asyncMills));
                Response<QueryRespModel> response = FeignDumplingsResponseRunner.of(() -> queryRestService.getResp(queryResp.getData().getId()), log);
                ret = response.getData();
            } else {
                //理想曲线才能拥有更好的性能
                long preStepMills = asyncMills / maxReq;
                for (int si = 0; si < maxReq; si++) {
                    Try.run(() -> TimeUnit.MILLISECONDS.sleep(preStepMills));
                    Response<QueryRespModel> response = FeignDumplingsResponseRunner.of(() -> queryRestService.getResp(queryResp.getData().getId()), log);
                    if (Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode())) {
                        QueryRespModel resp = response.getData();
                        ret = resp;
                        if (isEndPoint(resp)) {
                            break;
                        }
                    }
                }
            }
            TraceFuture.run(() -> log.info("EngineService queryAndWait complete (async) ! cost:{}`", System.currentTimeMillis() - startMills));
            return ret;
        }
    }

    @Override
    public <T> T commonQuery(ClusterInfo clusterInfo, String sql, Mapper<T> mapper) {
        return Try.of(() -> {
                    QueryReqModel queryReq = QueryReqModel.builder()
                            .connectionInfo(cover2ConnectionInfo(clusterInfo))
                            .templateSql(sql)
                            .build();
                    supplyCommonProperties(queryReq);
                    QueryRespModel resp = queryAndWait(queryReq);
                    return mapper.apply(resp);
                })
                .onFailure(ex -> log.error(String.format("EngineServiceImpl commonQuery error ! sql:【%s】", sql), ex))
                .getOrNull();
    }

    private ConnectionInfoModel cover2ConnectionInfo(ClusterInfo clusterInfo) {
        return ConnectionInfoModel.builder()
                .name(clusterInfo.getName())
                .userName(clusterInfo.getUserName())
                .password(clusterInfo.getPassword())
                .defaultDatabase(clusterInfo.getDefaultDatabase())
                .address(clusterInfo.getAddress())
                .type(DsType.findByFlag(clusterInfo.getType()))
                .defaultSchema(clusterInfo.getDefaultSchema())
                .comment(clusterInfo.getComment())
                .build();
    }

}
