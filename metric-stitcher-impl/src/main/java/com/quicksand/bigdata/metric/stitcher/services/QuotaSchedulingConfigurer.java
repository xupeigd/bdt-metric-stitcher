package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaDBVO;
import com.quicksand.bigdata.metric.stitcher.repos.QuotaAutoRepo;
import com.quicksand.bigdata.metric.stitcher.vos.QuotaVO;
import com.quicksand.bigdata.vars.util.JsonUtils;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * QuotaSchedulingConfigurer
 *
 * @author page
 * @date 2022/12/16
 */
@Slf4j
@Component
public class QuotaSchedulingConfigurer
        implements SchedulingConfigurer {

    /**
     * scheduledFutures
     * <p>
     * key {appId}:{metricId}
     * value  instance of ScheduledFuture
     */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @Resource
    QuotaAutoRepo quotaAutoRepo;
    @Resource
    MetricQuotaService metricQuotaService;

    private volatile ScheduledTaskRegistrar registrar;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        //设置20个线程,默认单线程,如果不设置的话，不能同时并发执行任务
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(30));
        this.registrar = taskRegistrar;
    }

    public void refresh(List<QuotaDBVO> workQuotas) {
        if (!CollectionUtils.isEmpty(workQuotas)) {
            Map<String, QuotaVO> key2Quotas = workQuotas.stream()
                    .collect(Collectors.toMap(k -> String.format("%d:%d", k.getAppId(), k.getMetricId()),
                            v -> JsonUtils.transfrom(v, QuotaVO.class)));
            Map<String, ScheduledFuture<?>> hitFutures = new HashMap<>();
            for (Map.Entry<String, QuotaVO> entry : key2Quotas.entrySet()) {
                if (scheduledFutures.containsKey(entry.getKey())) {
                    hitFutures.put(entry.getKey(), scheduledFutures.get(entry.getKey()));
                }
            }
            //取消正在执行的作业
            if (!hitFutures.isEmpty()) {
                hitFutures.values().forEach(v -> v.cancel(true));
                hitFutures.keySet().forEach(scheduledFutures::remove);
            }
            //注册新的任务
            for (Map.Entry<String, QuotaVO> entry : key2Quotas.entrySet()) {
                if (null != quotaAutoRepo && null != metricQuotaService && null != registrar) {
                    Try.run(() -> {
                                QuotaCronTask quotaCronTask = new QuotaCronTask(entry.getValue(), quotaAutoRepo, metricQuotaService);
                                @SuppressWarnings("ConstantConditions") ScheduledFuture<?> schedule = registrar.getScheduler()
                                        .schedule(quotaCronTask.getRunnable(), quotaCronTask.getTrigger());
                                if (null != schedule) {
                                    scheduledFutures.put(entry.getKey(), schedule);
                                }
                            })
                            .onFailure(ex -> log.warn("refresh workQuotas schedule error ! task:{}`", JsonUtils.toJsonString(entry.getValue())));
                }
            }
        }
    }

    @PreDestroy
    public void destroy() {
        if (null != registrar) {
            this.registrar.destroy();
        }
    }

    public static class QuotaCronTask
            extends CronTask {

        @Getter
        QuotaVO quota;

        public QuotaCronTask(QuotaVO quota, QuotaAutoRepo quotaAutoRepo, MetricQuotaService metricQuotaService) {
            super(new QuotaRunnable(quota, quotaAutoRepo, metricQuotaService), quota.getCronExpress());
            this.quota = quota;
        }

        @AllArgsConstructor
        public static final class QuotaRunnable
                implements Runnable {

            QuotaVO quota;
            QuotaAutoRepo quotaAutoRepo;
            MetricQuotaService metricQuotaService;

            @Override
            public void run() {
                List<QuotaDBVO> dbvos = quotaAutoRepo.findByAppIdAndMetricIdOrderByUpdateTimeDesc(quota.getAppId(), quota.getMetricId());
                if (!CollectionUtils.isEmpty(dbvos)) {
                    QuotaDBVO dbvo = dbvos.get(0);
                    if (Objects.equals(quota.getFlag(), dbvo.getFlag())) {
                        //执行刷新逻辑
                        logicFunction(quota);
                    } else {
                        log.info("QuotaRunnable conflict ! id:{}`orginalFlag:{}`curFlag:{}`", quota.getId(), quota.getFlag(), dbvo.getFlag());
                    }
                }
            }

            private void logicFunction(QuotaVO quota) {
                metricQuotaService.refreshQuota(quota);
            }
        }

    }

}
