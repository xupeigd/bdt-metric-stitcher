package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.quicksand.bigdata.metric.management.apis.rest.InvokePlatformRestService;
import com.quicksand.bigdata.metric.management.datasource.models.ClusterInfoModel;
import com.quicksand.bigdata.metric.stitcher.services.ClusterLAService;
import com.quicksand.bigdata.vars.http.JstAppInfo;
import com.quicksand.bigdata.vars.http.JstInfo;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.JsonUtils;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * ClusterLAServiceImpl
 *
 * @author page
 * @date 2022/11/16
 */
@Slf4j
@Service
public class ClusterLAServiceImpl
        extends com.quicksand.bigdata.metric.stitcher.services.impls.StitcherSecService
        implements ClusterLAService {

    private static final ClusterInfoModel NULL = new ClusterInfoModel();

    @Resource
    InvokePlatformRestService invokePlatformRestService;

    private final LoadingCache<Integer, ClusterInfoModel> clusterInfoCaches = CacheBuilder
            .newBuilder()
            .maximumSize(100L)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, ClusterInfoModel>() {
                @Override
                public ClusterInfoModel load(Integer key) {
                    return remoteFetchClusterInfo(key);
                }
            });

    private ClusterInfoModel remoteFetchClusterInfo(Integer clusterId) {
        String orginalJstInfo = JstInfo.excavate();
        String orginalJstAppInfo = JstAppInfo.excavate();
        return Try.of(() -> {
                    JstInfo.make("");
                    JstAppInfo.make(buildStitcherSignHeader());
                    Response<List<ClusterInfoModel>> remoteResponse = invokePlatformRestService.fecthAllClusterInfos(Lists.newArrayList(clusterId));
                    if (null != remoteResponse
                            && Objects.equals(String.valueOf(HttpStatus.OK.value()), remoteResponse.getCode())
                            && !CollectionUtils.isEmpty(remoteResponse.getData())) {
                        return remoteResponse.getData().get(0);
                    } else {
                        log.warn("remoteFetchClusterInfo fail ! clusterId:{},resp:{}", clusterId, JsonUtils.toJsonString(remoteResponse));
                    }
                    return NULL;
                })
                .onFailure(ex -> log.error("ClusterLAServiceImpl remoteFetchClusterInfo fail ! clusterId:" + clusterId, ex))
                .andFinally(() -> {
                    JstInfo.make(orginalJstInfo);
                    JstAppInfo.make(orginalJstAppInfo);
                })
                .getOrElse(NULL);
    }

    @XxlJob("loadClusterInfos")
    @Scheduled(initialDelay = 30 * 1000L, fixedRate = 5 * 60 * 1000L)
    public void loadClusterInfos() {
        Try.run(() -> {
                    JstAppInfo.make(buildStitcherSignHeader());
                    Response<List<ClusterInfoModel>> remoteResponse = FeignDumplingsResponseRunner.of(() -> invokePlatformRestService.fecthAllClusterInfos(Collections.emptyList()), log);
                    if (null != remoteResponse
                            && Objects.equals(String.valueOf(HttpStatus.OK.value()), remoteResponse.getCode())
                            && !CollectionUtils.isEmpty(remoteResponse.getData())) {
                        for (ClusterInfoModel clusterInfoModel : remoteResponse.getData()) {
                            synchronized (clusterInfoCaches) {
                                clusterInfoCaches.put(clusterInfoModel.getId(), clusterInfoModel);
                            }
                        }
                        XxlJobHelper.handleSuccess("载入完成！");
                    } else {
                        log.warn("loadClusterInfos fail ! resp:{}", JsonUtils.toJsonString(remoteResponse));
                        XxlJobHelper.handleFail("载入失败！");
                    }
                })
                .onFailure(ex -> log.error("loadClusterInfos fail ! ", ex));
    }


    @Override
    public ClusterInfoModel findClusterInfo(int clusterId) {
        ClusterInfoModel maybeValue = Try.of(() -> clusterInfoCaches.get(clusterId)).getOrElse(NULL);
        return Objects.equals(NULL, maybeValue) ? null : maybeValue;
    }

}
