package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.stitcher.services.MetricQpsService;
import com.quicksand.bigdata.vars.concurrents.TraceFuture;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MetricQpsServiceImpl
 *
 * @author page
 * @date 2022/12/22
 */
@Slf4j
@Service
public class MetricQpsServiceImpl
        implements MetricQpsService {

    @SuppressWarnings("rawtypes")
    @Resource
    RedisScript qpsLogScript;
    @Resource
    RedisTemplate<String, String> stringRedisTemplate;

    @Override
    public void incr(int appId, List<Integer> metricIds) {
        Map<String, Long> key2qps = metricIds.stream()
                .map(v -> new AbstractMap.SimpleEntry<>(String.format(MetricQuotaServiceImpl.RK_QUOTA_COUNT_TEMPLATE_MAX_OPS, appId, v),
                        stringRedisTemplate.opsForValue().increment(String.format(RK_METRIC_QPS, appId, v), 1L)))
                .filter(v -> null != v.getValue() && 0L < v.getValue())
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        if (!CollectionUtils.isEmpty(key2qps)) {
            //顺次比对
            key2qps.forEach((k, v) -> Try.run(() -> {
                        String rkQps = k.replace("P:", "STIT:TQPS:");
                        stringRedisTemplate.expire(rkQps, 1, TimeUnit.SECONDS);
                        List<String> keys = new ArrayList<>();
                        keys.add(MetricQuotaServiceImpl.RK_QUOTA_COUNT);
                        keys.add(k);
                        //noinspection unchecked
                        stringRedisTemplate.execute(qpsLogScript, keys, String.valueOf(v));
                    })
                    .onFailure(ex -> log.error("execute qpsLogScript fail ! ", ex)));
        }
    }

    @Override
    public void reduce(int appId, List<Integer> metricIds, boolean candidate, long costMills) {
        if (!CollectionUtils.isEmpty(metricIds)) {
            TraceFuture.run(() -> Try.run(() -> {
                        metricIds.forEach(metricId -> {
                            String rkQps = String.format(RK_METRIC_QPS, appId, metricId);
                            stringRedisTemplate.opsForValue().increment(rkQps, -1L);
                            stringRedisTemplate.expire(rkQps, 1, TimeUnit.SECONDS);
                        });
                        Date date = new Date();
                        SimpleDateFormat yyyyMMddHHmmss = new SimpleDateFormat("yyyyMMddHHmmss");
                        Set<ZSetOperations.TypedTuple<String>> typlus = metricIds.stream()
                                .map(v -> ZSetOperations.TypedTuple.of(String.format(candidate ? "C:%d:%d:%d:%s" : "Q:%d:%d:%d:%s", appId, v, costMills, yyyyMMddHHmmss.format(date)), 1D * date.getTime()))
                                .collect(Collectors.toSet());
                        String rkTls = String.format(RK_INVOKE_TLS, new SimpleDateFormat("yyyyMMdd").format(date));
                        stringRedisTemplate.opsForZSet().add(rkTls, typlus);
                        stringRedisTemplate.expire(rkTls, 72L, TimeUnit.HOURS);
                    })
                    .onFailure(ex -> log.error("reduce async execute fail!", ex)));
        }
    }

    @Override
    public long curDaycost(int appId, int metricId, Date date) {
        long count = 0L;
        SimpleDateFormat yyyyMMddHHmmss = new SimpleDateFormat("yyyyMMdd");
        String rkTls = String.format(RK_INVOKE_TLS, yyyyMMddHHmmss.format(date));
        Cursor<ZSetOperations.TypedTuple<String>> cursor = stringRedisTemplate.opsForZSet().scan(rkTls, ScanOptions.scanOptions().match(String.format("[QC]:%d:%d:*", appId, metricId)).build());
        while (cursor.hasNext()) {
            count++;
            cursor.next();
        }
        Try.run(cursor::close);
        return count;
    }

}
