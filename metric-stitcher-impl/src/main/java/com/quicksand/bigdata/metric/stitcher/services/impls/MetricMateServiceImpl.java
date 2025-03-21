package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.management.consts.PubsubStatus;
import com.quicksand.bigdata.metric.management.metric.models.MetricDetailModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricOverviewModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricQueryModel;
import com.quicksand.bigdata.metric.management.metric.rests.MetricManageRestService;
import com.quicksand.bigdata.metric.management.metric.rests.MetricRestService;
import com.quicksand.bigdata.metric.stitcher.services.MetricMateService;
import com.quicksand.bigdata.vars.http.JstAppInfo;
import com.quicksand.bigdata.vars.http.JstInfo;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.PageImpl;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MetricMateServiceImpl
 *
 * @author page
 * @date 2023/2/16
 */
@Slf4j
@Service
public class MetricMateServiceImpl
        extends StitcherSecService
        implements MetricMateService {

    public static final byte[] LOCK = new byte[0];
    static final Map<Integer, Integer> publishIds = new ConcurrentHashMap<>();
    static final Map<String, Integer> code2Ids = new ConcurrentHashMap<>();
    static final Map<String, Integer> name2Ids = new ConcurrentHashMap<>();
    @Resource
    MetricRestService metricRestService;
    @Resource
    MetricManageRestService metricManageRestService;

    @Scheduled(initialDelay = 30 * 1000L, fixedRate = 5 * 60 * 1000L)
    public void loadMetricMetas() {
        Map<Integer, Integer> tmpPublishIds = new HashMap<>();
        Map<String, Integer> tmpCode2Ids = new HashMap<>();
        Map<String, Integer> tmpName2Ids = new HashMap<>();
        int curPage = 1;
        final int pageSize = 100;
        long total = pageSize + 1L;
        while ((long) curPage * pageSize < total) {
            int finalStartPage = curPage;
            Response<PageImpl<MetricOverviewModel>> publishMetricPage = Try.of(() -> {
                        JstInfo.make("");
                        JstAppInfo.make(buildStitcherSignHeader());
                        return FeignDumplingsResponseRunner.<PageImpl<MetricOverviewModel>>of(() -> metricManageRestService.listMetrics(MetricQueryModel.builder()
                                .pubsubs(Collections.singletonList(PubsubStatus.Online.getCode()))
                                .pageNo(finalStartPage)
                                .pageSize(pageSize)
                                .build()), log);
                    })
                    .onFailure(ex -> log.error("loadMetricMetas fail !", ex))
                    .getOrNull();
            if (null != publishMetricPage && null != publishMetricPage.getData()) {
                PageImpl<MetricOverviewModel> page = publishMetricPage.getData();
                total = page.getTotal();
                curPage += 1;
                if (!CollectionUtils.isEmpty(page.getItems())) {
                    for (MetricOverviewModel data : page.getItems()) {
                        tmpPublishIds.put(data.getId(), data.getId());
                        tmpCode2Ids.put(data.getSerialNumber(), data.getId());
                        tmpName2Ids.put(data.getEnName(), data.getId());
                    }
                }
            }
        }
        //load完整以后，进行替换
        synchronized (LOCK) {
            publishIds.clear();
            publishIds.putAll(tmpPublishIds);
            code2Ids.clear();
            code2Ids.putAll(tmpCode2Ids);
            name2Ids.clear();
            name2Ids.putAll(tmpName2Ids);
        }
    }

    @Override
    public boolean existMetric(int metricId) {
        boolean exist = publishIds.containsKey(metricId);
        if (!exist) {
            //阻塞查找
            Response<MetricDetailModel> response = Try.of(() -> {
                        JstInfo.make("");
                        JstAppInfo.make(buildStitcherSignHeader());
                        return FeignDumplingsResponseRunner.<MetricDetailModel>of(() -> metricRestService.findMetricById(metricId));
                    })
                    .onFailure(ex -> log.error("existMetric fail !", ex))
                    .getOrNull();
            MetricDetailModel metric = null != response
                    && null != response.getData()
                    && Objects.equals(PubsubStatus.Online, response.getData().getPubsub())
                    ? response.getData() : null;
            if (null != metric) {
                synchronized (LOCK) {
                    publishIds.put(metric.getId(), metric.getId());
                    code2Ids.put(metric.getSerialNumber(), metric.getId());
                    name2Ids.put(metric.getEnName(), metric.getId());
                }
            }
            exist = null != metric;
        }
        return exist;
    }

    @Override
    public Integer findMetricId(String keyword) {
        //先找
        Integer metricId = code2Ids.getOrDefault(keyword, -1);
        metricId = -1 == metricId ? name2Ids.getOrDefault(keyword, -1) : metricId;
        if (-1 == metricId) {
            //阻塞查找
            Response<MetricOverviewModel> response = Try.of(() -> {
                        JstInfo.make("");
                        JstAppInfo.make(buildStitcherSignHeader());
                        return FeignDumplingsResponseRunner.<MetricOverviewModel>of(() -> metricRestService.findMetric(keyword, keyword));
                    })
                    .onFailure(ex -> log.error("findMetricId fail !", ex))
                    .getOrNull();

            MetricOverviewModel overviewModel = null != response
                    && null != response.getData()
                    && Objects.equals(PubsubStatus.Online, response.getData().getPubsub())
                    ? response.getData() : null;
            if (null != overviewModel) {
                synchronized (LOCK) {
                    publishIds.put(overviewModel.getId(), overviewModel.getId());
                    code2Ids.put(overviewModel.getSerialNumber(), overviewModel.getId());
                    name2Ids.put(overviewModel.getEnName(), overviewModel.getId());
                }
                metricId = overviewModel.getId();
            }
        }
        return metricId;
    }

}
