package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.metric.management.apis.models.QuotaCostModel;
import com.quicksand.bigdata.metric.management.apis.models.QuotaCostPackageModel;
import com.quicksand.bigdata.metric.management.apis.models.QuotaModel;
import com.quicksand.bigdata.metric.management.apis.models.QuotaPackageModel;
import com.quicksand.bigdata.metric.management.apis.rest.InvokeQuotaCostRestService;
import com.quicksand.bigdata.metric.management.apis.rest.InvokeQuotaRestService;
import com.quicksand.bigdata.metric.management.consts.DataStatus;
import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaCostDBVO;
import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaDBVO;
import com.quicksand.bigdata.metric.stitcher.repos.QuotaAutoRepo;
import com.quicksand.bigdata.metric.stitcher.repos.QuotaCostAutoRepo;
import com.quicksand.bigdata.metric.stitcher.services.MetricQpsService;
import com.quicksand.bigdata.metric.stitcher.services.MetricQuotaService;
import com.quicksand.bigdata.metric.stitcher.services.QuotaSchedulingConfigurer;
import com.quicksand.bigdata.metric.stitcher.vos.QuotaVO;
import com.quicksand.bigdata.vars.http.JstAppInfo;
import com.quicksand.bigdata.vars.http.JstInfo;
import com.quicksand.bigdata.vars.http.TraceId;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import com.quicksand.bigdata.vars.util.FeignDumplingsResponseRunner;
import com.quicksand.bigdata.vars.util.JsonUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MetricQuotaServiceImpl
 *
 * @author page
 * @date 2022/11/22
 */
@Slf4j
@Service
public class MetricQuotaServiceImpl
        extends StitcherSecService
        implements MetricQuotaService {

    /**
     * quota的key
     * zset
     * <p>
     * value:String:
     * <p>
     * L:${appId}:${metricId} 最后计数时间
     * Q:${appId}:${metricId} 当前的消耗数据
     * <p>
     * M:%s 额度限制数据（MAX）
     * S:%s 额度限制的最后计数时间
     * <p>
     * score:count
     */
    public static final String RK_QUOTA_COUNT = "STI:VARS:QUOTAS";

    public static final String RK_QUOTA_COUNT_TEMPLATE_COST_LASTLOG = "L:%d:%d";
    public static final String RK_QUOTA_COUNT_TEMPLATE_QUOTA_COST = "Q:%d:%d";
    public static final String RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA = "M:%s";
    public static final String RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA_LASTLOG = "S:%s";
    public static final String RK_QUOTA_COUNT_TEMPLATE_MAX_OPS = "P:%d:%d";

    public static final Map<String, String> APP_METRIC_2_FLAG = new ConcurrentHashMap<>();
    public static final String RK_LOCK_ORMANDSYNC = "STI:LCK:OASC";
    public static final String RL_LOCK_REFRESH_TEMPLATE = "STI:LCK:OASC:%d:%d";
    public static final String RK_LOCK_CLEAN_ZSET = "STI:LCK:CN";
    public static volatile String QUOTA_FLAG = "";

    @Value("${vars.stitcher.quota.limit:true}")
    boolean quotaLimit;
    @Value("${vars.stitcher.quota.cost.sync:true}")
    boolean syncQuotaCost;

    @Resource
    QuotaAutoRepo quotaAutoRepo;
    @Resource
    QuotaCostAutoRepo quotaCostAutoRepo;
    @Resource
    InvokeQuotaRestService invokeQuotaRestService;
    @Resource
    RedisTemplate<String, String> stringRedisTemplate;
    @Resource
    QuotaSchedulingConfigurer quotaSchedulingConfigurer;
    @Resource
    InvokeQuotaCostRestService invokeQuotaCostRestService;
    @Resource
    MetricQpsService metricQpsService;

    @PostConstruct
    public void init() {
        Map<String, String> tmpAppMetric2Flag;
        Set<ZSetOperations.TypedTuple<String>> tuples;
        //启动后尝试扫描进行对照
        List<QuotaDBVO> quotaDBVOS = quotaAutoRepo.findAll();
        if (!CollectionUtils.isEmpty(quotaDBVOS)) {
            tmpAppMetric2Flag = quotaDBVOS.stream()
                    .collect(Collectors.toMap(k -> String.format("%d:%d", k.getAppId(), k.getMetricId()), QuotaDBVO::getFlag));
            //放置对应的MAX值
            tuples = quotaDBVOS.stream()
                    .map(v -> ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA, v.getFlag()), 1D * v.getQuota()))
                    .collect(Collectors.toSet());
            tuples.addAll(quotaDBVOS.stream()
                    .map(v -> ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA_LASTLOG, v.getFlag()), 1D * System.currentTimeMillis()))
                    .collect(Collectors.toSet()));
            synchronized (this) {
                stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, tuples);
                APP_METRIC_2_FLAG.clear();
                APP_METRIC_2_FLAG.putAll(tmpAppMetric2Flag);
            }
            quotaSchedulingConfigurer.refresh(quotaDBVOS);
        } else {
            log.info("local quotas not exist ! ");
        }
        //载入消耗数据
        preloadQuotaCost();
        //紧接着听从远古的呼唤
        preloadQuotaConfigs();
    }

    /**
     * 只需要载入配置中有的
     */
    private void preloadQuotaCost() {
        List<QuotaCostDBVO> quotaCostDBVOS = quotaCostAutoRepo.findAllByNextRefreshDateAfterOrderByUpdateTimeDesc(new Date()).stream()
                .filter(v -> 0 < v.getCurCost())
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(quotaCostDBVOS)) {
            //逐个载入
            for (QuotaCostDBVO dbvo : quotaCostDBVOS) {
                String lastQuotaKey = String.format(RK_QUOTA_COUNT_TEMPLATE_QUOTA_COST, dbvo.getAppId(), dbvo.getMetricId());
                String lastQuotaLogKey = String.format(RK_QUOTA_COUNT_TEMPLATE_COST_LASTLOG, dbvo.getAppId(), dbvo.getMetricId());
                Double lastLogMills = Try.of(() -> stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, lastQuotaLogKey, 0D)).get();
                if (null == lastLogMills || 0D == lastLogMills || lastLogMills < dbvo.getUpdateTime().getTime()) {
                    //以数据库为准载入
                    Try.run(() -> stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, lastQuotaKey, 1D * dbvo.getCurCost()));
                    Try.run(() -> stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, lastQuotaLogKey, 1D * dbvo.getUpdateTime().getTime()));
                }
            }
        }

    }

    /**
     * 每1分钟重载一次配额数据
     *
     * @warn 预载数据不会包含内部应用的权限（内部应用拥有所有指标的权限）
     */
    @XxlJob("preloadQuotaConfigs")
    @Scheduled(initialDelay = 30 * 1000L, fixedRate = 60 * 1000L)
    public void preloadQuotaConfigs() {
        String curFlag = QUOTA_FLAG;
        Response<QuotaPackageModel> response = Try.of(() -> {
                    JstInfo.make("");
                    JstAppInfo.make(buildStitcherSignHeader());
                    return FeignDumplingsResponseRunner.<QuotaPackageModel>of(() -> invokeQuotaRestService.fetchAllQuotas(curFlag, null, null), log);
                })
                .onFailure(e -> log.error("preloadQuotaConfigs fail ! ", e))
                .get();
        if (null != response
                && Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode())
                && null != response.getData()) {
            QuotaPackageModel quotaPackageModel = response.getData();
            if (StringUtils.hasText(quotaPackageModel.getFlag())) {
                if (StringUtils.hasText(curFlag) && Objects.equals(curFlag, quotaPackageModel.getFlag())) {
                    log.info("preloadQuotaConfigs done! quota data no change ! ");
                } else if (!CollectionUtils.isEmpty(quotaPackageModel.getQuotas())) {
                    Date operationTime = new Date();
                    Map<String, String> tmpAppMetric2Flag = new HashMap<>();
                    Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
                    //分解数据，并载入Redis
                    List<QuotaDBVO> existQuotas = quotaAutoRepo.findAll();
                    Map<String, QuotaDBVO> quotaDBVOMap = new HashMap<>();
                    for (QuotaDBVO existQuota : existQuotas) {
                        String dateKey = String.format("%d:%d", existQuota.getAppId(), existQuota.getMetricId());
                        if (!quotaDBVOMap.containsKey(dateKey)) {
                            quotaDBVOMap.put(dateKey, existQuota);
                        }
                    }
                    List<QuotaDBVO> saveDbvos = new ArrayList<>();
                    Map<String, QuotaDBVO> loadDbvos = new HashMap<>();
                    //扫描结果
                    for (QuotaModel qm : quotaPackageModel.getQuotas()) {
                        String appMetricKey = String.format("%d:%d", qm.getAppId(), qm.getMetricId());
                        QuotaDBVO quotaDBVO = quotaDBVOMap.get(appMetricKey);
                        if (null == quotaDBVO) {
                            //新建
                            quotaDBVO = QuotaDBVO.builder()
                                    .sourceId(qm.getId())
                                    .appId(qm.getAppId())
                                    .metricId(qm.getMetricId())
                                    .status(DataStatus.ENABLE)
                                    .createTime(operationTime)
                                    .build();
                        }
                        quotaDBVO.setAppType(qm.getAppType());
                        quotaDBVO.setUpdateTime(operationTime);
                        quotaDBVO.setFlag(qm.getExchangeFlag());
                        quotaDBVO.setQuota(qm.getQuota());
                        quotaDBVO.setCronExpress(qm.getRefreshCornExpress());
                        loadDbvos.put(appMetricKey, quotaDBVO);
                        saveDbvos.add(quotaDBVO);
                        tmpAppMetric2Flag.put(appMetricKey, qm.getExchangeFlag());
                        tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA, qm.getExchangeFlag()), 1D * qm.getQuota()));
                        tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA_LASTLOG, qm.getExchangeFlag()), 1D * operationTime.getTime()));
                    }
                    //移除不在列表的数据
                    quotaDBVOMap.forEach((k, v) -> {
                        if (!loadDbvos.containsKey(k)) {
                            //是否内部应用的
                            if (-1L == v.getQuota()
                                    && Objects.equals(String.format("%d:%d", v.getAppId(), v.getMetricId()), v.getFlag())) {
                                String appMetricKey = String.format("%d:%d", v.getAppId(), v.getMetricId());
                                tmpAppMetric2Flag.put(appMetricKey, v.getFlag());
                                tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA, v.getFlag()), 1D * v.getQuota()));
                                tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA_LASTLOG, v.getFlag()), 1D * System.currentTimeMillis()));
                            } else {
                                v.setStatus(DataStatus.DISABLE);
                                v.setUpdateTime(operationTime);
                                saveDbvos.add(v);
                            }
                        }
                    });
                    synchronized (this) {
                        if (Objects.equals(curFlag, QUOTA_FLAG)) {
                            stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, tuples);
                            APP_METRIC_2_FLAG.clear();
                            APP_METRIC_2_FLAG.putAll(tmpAppMetric2Flag);
                            //入库
                            quotaAutoRepo.saveAll(saveDbvos);
                            //更新flag
                            QUOTA_FLAG = quotaPackageModel.getFlag();
                            quotaSchedulingConfigurer.refresh(saveDbvos.stream()
                                    .filter(v -> Objects.equals(DataStatus.ENABLE, v.getStatus()))
                                    .collect(Collectors.toList()));
                        } else {
                            log.info("preloadQuotaConfigs conflict : abandon this response ! curKey:{}`nowKey:{}`", curFlag, QUOTA_FLAG);
                        }
                    }
                }
            }
        } else {
            log.warn("preloadQuotaConfigs fail ! resp:{}", JsonUtils.toJsonString(response));
        }
    }

    /**
     * 每5分钟持久化/通同步一次cost数据
     * (重操作)
     */
    @XxlJob("ormAndSyncCost")
    @Scheduled(initialDelay = 30 * 1000L, fixedRate = 5 * 60 * 1000L)
    public void ormAndSyncCost() {
        SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
        Date operationTime = new Date();
        Cursor<ZSetOperations.TypedTuple<String>> cusror = stringRedisTemplate.opsForZSet()
                .scan(RK_QUOTA_COUNT, ScanOptions.scanOptions().match("[QLP]:*").count(10000L).build());
        TreeMap<String, Double> key2Scores = new TreeMap<>();
        while (cusror.hasNext()) {
            ZSetOperations.TypedTuple<String> next = cusror.next();
            key2Scores.put(next.getValue(), next.getScore());
        }
        Try.run(cusror::close)
                .onFailure(ex -> log.warn("ormAndSyncCost cusror close fail !", ex));
        if (key2Scores.isEmpty()) {
            log.info("ormAndSyncCost done : key2Scores is empty! ");
            return;
        }
        //信号放一把
        Boolean setState = stringRedisTemplate.opsForValue().setIfAbsent(RK_LOCK_ORMANDSYNC, String.valueOf(System.currentTimeMillis()), 30L, TimeUnit.SECONDS);
        if (null == setState || Boolean.FALSE.equals(setState)) {
            log.info("ormAndSyncCost done : setState : {} ! ", setState);
            return;
        }
        Map<String, Double> quotaKey2Scores = key2Scores.entrySet().stream()
                .filter(v -> v.getKey().startsWith("Q:"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Double> quotaLastLogKey2Scores = key2Scores.entrySet().stream()
                .filter(v -> v.getKey().startsWith("L:"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Double> qpsKey2Scores = key2Scores.entrySet().stream()
                .filter(v -> v.getKey().startsWith("P:"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<QuotaCostModel> syncCosts = new ArrayList<>();
        //基准时间
        Date baseUpdateTime = Try.of(() -> SDF.parse(String.format("%s 23:59:59", yyyyMMdd.format(operationTime)))).get();
        quotaKey2Scores.forEach((k, v) -> {
            String[] keys = k.split(":");
            Double lastLogMills = quotaLastLogKey2Scores.get(k.replace("Q:", "L:"));
            if (null == lastLogMills || lastLogMills < System.currentTimeMillis() - 24 * 60 * 60 * 1000L) {
                //最后记录时间超过24H的不再进行同步
                return;
            }
            Double qpsScore = qpsKey2Scores.get(k.replace("Q:", "P:"));
            int appId = Integer.parseInt(keys[1]);
            int metricId = Integer.parseInt(keys[2]);
            String flag = APP_METRIC_2_FLAG.get(String.format("%d:%d", appId, metricId));
            QuotaDBVO quotaDBVO = StringUtils.hasText(flag) ? quotaAutoRepo.findByFlag(flag) : null;
            Date nextRefreshDate = null == quotaDBVO || !StringUtils.hasText(quotaDBVO.getCronExpress())
                    ? Try.of(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2099-12-31 23:59:59")).get()
                    : Try.of(() -> {
                        CronExpression cronExpression = CronExpression.parse(quotaDBVO.getCronExpress());
                        LocalDateTime localDateTime = cronExpression.next(LocalDateTime.now());
                        //noinspection ConstantConditions
                        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                    })
                    .get();
            String workFlag = flag + "|" + SDF.format(nextRefreshDate);
            //按天寻找数据
            List<QuotaCostDBVO> quotaCostDBVOS = quotaCostAutoRepo.findByAppIdAndMetricIdAndFlagLikeAndLogDateAndNextRefreshDateAfterOrderByUpdateTimeDesc(appId, metricId, workFlag, baseUpdateTime, operationTime);
            if (CollectionUtils.isEmpty(quotaCostDBVOS)) {
                quotaCostDBVOS = new ArrayList<>();
                quotaCostDBVOS.add(QuotaCostDBVO.builder()
                        .appId(appId)
                        .metricId(metricId)
                        .status(DataStatus.ENABLE)
                        .createTime(operationTime)
                        .logDate(baseUpdateTime)
                        .nextRefreshDate(nextRefreshDate)
                        .flag(workFlag)
                        .build());
            } else if (Objects.equals(quotaCostDBVOS.get(0).getCurCost(), v.longValue())) {
                //消耗数据没办法且有实体的，不再orm
                return;
            }
            QuotaCostDBVO effectivedDbvo = quotaCostDBVOS.get(0);
            effectivedDbvo.setCurCost(v.longValue());
            effectivedDbvo.setDayCost(metricQpsService.curDaycost(appId, metricId, operationTime));
            effectivedDbvo.setUpdateTime(operationTime);
            effectivedDbvo.setMaxQps(null == qpsScore ? 0 : qpsScore.intValue());
            if (1 < quotaCostDBVOS.size()) {
                for (int i = 0; i < quotaCostDBVOS.size(); i++) {
                    if (0 < i) {
                        quotaCostDBVOS.get(i).setStatus(DataStatus.DISABLE);
                        quotaCostDBVOS.get(i).setUpdateTime(operationTime);
                    }
                }
            }
            //转换
            syncCosts.add(QuotaCostModel.builder()
                    .appId(appId)
                    .metricId(metricId)
                    .quota(null == quotaDBVO ? -1L : quotaDBVO.getQuota())
                    .cost(effectivedDbvo.getCurCost())
                    .dayCost(effectivedDbvo.getDayCost())
                    .dateFlag(workFlag)
                    .maxQps(null == qpsScore ? 0 : qpsScore.intValue())
                    .dateFlag(effectivedDbvo.getFlag())
                    .updateTime(operationTime)
                    .syncMills(lastLogMills.longValue())
                    .build());
            quotaCostAutoRepo.saveAll(quotaCostDBVOS);
        });
        if (syncCosts.isEmpty()) {
            log.info("ormAndSyncCost done : syncCosts is empty !");
            return;
        }
        syncCosts2Remote(syncCosts);
        log.info("ormAndSyncCost done size:{} !", syncCosts.size());
    }

    /**
     * 每30分钟执行一次
     * <p>
     * 超过value超过1000时进行检查
     */
    @XxlJob("clearRedisQuotaZset")
    @Scheduled(initialDelay = 30 * 1000L, fixedRate = 30 * 60 * 1000L)
    public void clearRedisQuotaZset() {
        Long count = stringRedisTemplate.opsForZSet().count(RK_QUOTA_COUNT, Double.MIN_VALUE, Double.MAX_VALUE);
        Boolean lockSuccess = false;
        if (null != count && 1000L <= count) {
            //执行清理过程
            lockSuccess = stringRedisTemplate.opsForValue().setIfAbsent(RK_LOCK_CLEAN_ZSET, String.valueOf(System.currentTimeMillis()), 30L, TimeUnit.SECONDS);
            if (Objects.equals(lockSuccess, true)) {
                Set<String> delKeys = new HashSet<>();
                Cursor<ZSetOperations.TypedTuple<String>> cursor = stringRedisTemplate.opsForZSet().scan(RK_QUOTA_COUNT, ScanOptions.scanOptions().match("*").count(5000L).build());
                Map<String, Double> keysScores = new HashMap<>();
                while (cursor.hasNext()) {
                    ZSetOperations.TypedTuple<String> next = cursor.next();
                    String key = next.getValue();
                    Double score = next.getScore();
                    assert key != null;
                    if (!key.startsWith("L:") && !key.startsWith("M:") && !key.startsWith("Q:") && !key.startsWith("S:") && !key.startsWith("P:")) {
                        delKeys.add(key);
                        continue;
                    }
                    keysScores.put(key, score);
                }
                Try.run(cursor::close);
                if (!keysScores.isEmpty()) {
                    keysScores.entrySet().stream().filter(v -> v.getKey().startsWith("M:"))
                            .forEach(v -> {
                                String lastLogVale = v.getKey().replace("M:", "S:");
                                Double lastMills = keysScores.get(lastLogVale);
                                if (null == lastMills || lastMills < System.currentTimeMillis() - 24 * 60 * 60 * 1000L) {
                                    delKeys.add(v.getKey());
                                    delKeys.add(lastLogVale);
                                }
                            });
                    keysScores.entrySet().stream().filter(v -> v.getKey().startsWith("Q:"))
                            .forEach(v -> {
                                String lastLogVale = v.getKey().replace("Q:", "L:");
                                Double lastMills = keysScores.get(lastLogVale);
                                if (null == lastMills || lastMills < System.currentTimeMillis() - 3 * 31 * 24 * 60 * 60 * 1000L) {
                                    delKeys.add(v.getKey());
                                    delKeys.add(lastLogVale);
                                }
                            });
                }
                if (!delKeys.isEmpty()) {
                    stringRedisTemplate.opsForZSet().remove(RK_QUOTA_COUNT, delKeys.toArray());
                }
            }
        }
        log.info("clearRedisQuotaZset done ! count:{} lockSuccess:{} ", count, lockSuccess);
    }

    private boolean syncCosts2Remote(List<QuotaCostModel> syncCosts) {
        if (!syncQuotaCost) {
            return true;
        } else {
            Response<QuotaCostPackageModel> response = Try.of(() -> {
                        JstInfo.make("");
                        JstAppInfo.make(buildStitcherSignHeader());
                        return FeignDumplingsResponseRunner.<QuotaCostPackageModel>of(() -> invokeQuotaCostRestService.syncQuotaCosts(syncCosts), log);
                    })
                    .onFailure(e -> log.error("syncQuotaCosts fail ! ", e))
                    .andFinally(() -> JstInfo.make(""))
                    .get();
            if (null != response && Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode()) && null != response.getData()) {
                log.info("syncQuotaCosts success ! ");
                return true;
            } else {
                log.warn("syncQuotaCosts fail ! resp:{}", JsonUtils.toJsonString(response));
            }
            return false;
        }
    }

    private QuotaModel remoteFetch(int appId, int metricId) {
        //从远端获取配额情况
        Response<QuotaPackageModel> response = Try.of(() -> {
                    JstInfo.make("");
                    JstAppInfo.make(buildStitcherSignHeader());
                    return FeignDumplingsResponseRunner.<QuotaPackageModel>of(() -> invokeQuotaRestService.fetchAllQuotas("", appId, metricId), log);
                })
                .onFailure(e -> log.error("preloadQuotaConfigs fail ! ", e))
                .get();
        if (null == response
                || !Objects.equals(String.valueOf(HttpStatus.OK.value()), response.getCode())
                || null == response.getData()
                || CollectionUtils.isEmpty(response.getData().getQuotas())) {
            return null;
        }
        Date operationTime = new Date();
        QuotaModel quotaModel = response.getData().getQuotas().get(0);
        //转换为dbvo
        List<QuotaDBVO> mayHitQuotas = quotaAutoRepo.findByAppIdAndMetricIdOrderByUpdateTimeDesc(appId, metricId);
        boolean refresh = false;
        for (QuotaDBVO v : mayHitQuotas) {
            if (Objects.equals(v.getFlag(), quotaModel.getExchangeFlag())) {
                v.setQuota(v.getQuota());
                v.setAppType(quotaModel.getAppType());
                v.setSourceId(quotaModel.getId());
                v.setUpdateTime(operationTime);
                v.setFlag(quotaModel.getExchangeFlag());
                v.setQuota(quotaModel.getQuota());
                v.setCronExpress(quotaModel.getRefreshCornExpress());
                refresh = true;
            } else {
                v.setStatus(DataStatus.DISABLE);
                v.setUpdateTime(operationTime);
            }
        }
        if (!refresh) {
            mayHitQuotas.add(QuotaDBVO.builder()
                    .sourceId(quotaModel.getId())
                    .appId(quotaModel.getAppId())
                    .appType(quotaModel.getAppType())
                    .metricId(quotaModel.getMetricId())
                    .quota(quotaModel.getQuota())
                    .cronExpress(quotaModel.getRefreshCornExpress())
                    .flag(StringUtils.hasText(quotaModel.getExchangeFlag()) ? quotaModel.getExchangeFlag() : "")
                    .status(DataStatus.ENABLE)
                    .createTime(operationTime)
                    .updateTime(operationTime)
                    .build());
        }
        synchronized (this) {
            quotaAutoRepo.saveAll(mayHitQuotas);
            APP_METRIC_2_FLAG.put(String.format("%d:%d", appId, metricId), quotaModel.getExchangeFlag());
            HashSet<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA, quotaModel.getExchangeFlag()), 1D * quotaModel.getQuota()));
            tuples.add(ZSetOperations.TypedTuple.of(String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA_LASTLOG, quotaModel.getExchangeFlag()), 1D * System.currentTimeMillis()));
            stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, tuples);
            //放置MAX值
            //重置flag，等待一下轮全局重置
            QUOTA_FLAG = quotaModel.getExchangeFlag();
            log.info("join quota ! appId:{}`metricId:{}`quota:{}`", appId, metricId, JsonUtils.toJsonString(quotaModel));
        }
        return quotaModel;
    }

    @Override
    public boolean overQuotaLimit(int metricId, AppRequestVO appRequestVO) {
        if (!quotaLimit) {
            return false;
        }
        if (null == appRequestVO) {
            return true;
        }
        //尝试读取flag
        String quotaFlag = APP_METRIC_2_FLAG.get(String.format("%d:%d", appRequestVO.getId(), metricId));
        if (!StringUtils.hasText(quotaFlag)) {
            //一般情况下，要么没有权限，要么新增的应用/指标，要么是内部应用
            QuotaModel quotaModel = remoteFetch(appRequestVO.getId(), metricId);
            if (null == quotaModel) {
                log.info("quota remoteFetch is null ! appId:{}`metricId:{}", appRequestVO.getId(), metricId);
                return true;
            }
            quotaFlag = quotaModel.getExchangeFlag();
        }
        if (Objects.equals(quotaFlag, String.format("%d:%d", appRequestVO.getId(), metricId))) {
            return false;
        }
        //取并发数(并发数已经计算当前在途的请求)
        Double processCount = stringRedisTemplate.opsForValue().increment(String.format(MetricQpsService.RK_METRIC_QPS, appRequestVO.getId(), metricId), 0D);
        processCount = null == processCount || 0D >= processCount ? 1D : processCount;
        //直接取2个数
        Double maxQuota = stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_QUOTA, quotaFlag), 0D);
        Double curQuota = stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, String.format(RK_QUOTA_COUNT_TEMPLATE_QUOTA_COST, appRequestVO.getId(), metricId), 0D);
        boolean limit = null != curQuota && null != maxQuota && maxQuota <= (curQuota + processCount);
        if (limit) {
            log.info("quota overlimit ! appId:{}`metricId:{}`max:{}`cur:{}`", appRequestVO.getId(), metricId, maxQuota, curQuota);
        }
        return limit;
    }

    @Override
    public void logQuotaCost(int metricId, AppRequestVO appRequestVO) {
        if (null == appRequestVO) {
            return;
        }
        if (quotaLimit) {
            String curQuotaKey = String.format(RK_QUOTA_COUNT_TEMPLATE_QUOTA_COST, appRequestVO.getId(), metricId);
            String lastQuotaKey = String.format(RK_QUOTA_COUNT_TEMPLATE_COST_LASTLOG, appRequestVO.getId(), metricId);
            Try.run(() -> stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, curQuotaKey, 1D))
                    .onFailure(ex -> log.error("logQuotaCost error ! values:" + curQuotaKey, ex));
            Try.run(() -> stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, lastQuotaKey, System.currentTimeMillis() * 1D))
                    .onFailure(ex -> log.error("logQuotaCost error ! values:" + lastQuotaKey, ex));
        }
    }

    @Override
    public void refreshQuota(QuotaVO quota) {
        Date operationTime = new Date();
        TraceId.make("SCHEDULED-" + UUID.randomUUID());
        log.info("refreshQuota ");
        Boolean setState = stringRedisTemplate.opsForValue().setIfAbsent(String.format(RL_LOCK_REFRESH_TEMPLATE, quota.getAppId(), quota.getMetricId()),
                String.valueOf(System.currentTimeMillis()), 30L, TimeUnit.SECONDS);
        if (null == setState || !setState) {
            log.info("refreshQuota conflict ! quota:{}", JsonUtils.toJsonString(quota));
            return;
        }
        List<QuotaCostModel> quotaCostModels = new ArrayList<>();
        SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
        String costDataValue = String.format(RK_QUOTA_COUNT_TEMPLATE_QUOTA_COST, quota.getAppId(), quota.getMetricId());
        String qpsDataValue = String.format(RK_QUOTA_COUNT_TEMPLATE_MAX_OPS, quota.getAppId(), quota.getMetricId());
        String costDataLastLogValue = String.format(RK_QUOTA_COUNT_TEMPLATE_COST_LASTLOG, quota.getAppId(), quota.getMetricId());
        //获取这一刻的数据
        Double curCost = stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, costDataValue, 0D);
        Double qpsScore = stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, qpsDataValue, 0D);
        curCost = null == curCost ? 0D : curCost;
        Date lastOperationTime = DateUtils.addSeconds(operationTime, -30);
        Date lastBaseUpdateTime = Try.of(() -> SDF.parse(String.format("%s 23:59:59", yyyyMMdd.format(lastOperationTime)))).get();
        List<QuotaCostDBVO> quotaCostDBVOs = quotaCostAutoRepo.findByAppIdAndMetricIdAndFlagLikeAndLogDateAndNextRefreshDateAfterOrderByUpdateTimeDesc(quota.getAppId(), quota.getMetricId(), quota.getFlag() + "|%",
                lastBaseUpdateTime, lastOperationTime);
        //处理当前
        if (CollectionUtils.isEmpty(quotaCostDBVOs)) {
            quotaCostDBVOs = new ArrayList<>();
            Date finalOperationTime1 = operationTime;
            Date curNextRefreshDate = Try.of(() -> {
                        CronExpression cronExpression = CronExpression.parse(quota.getCronExpress());
                        LocalDateTime localDateTime = cronExpression.next(DateUtils.addSeconds(finalOperationTime1, -30).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                        //noinspection ConstantConditions
                        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                    })
                    .get();
            curNextRefreshDate = null == curNextRefreshDate ? Try.of(() -> SDF.parse("2099-12-31 23:59:59")).get() : curNextRefreshDate;
            QuotaCostDBVO qcdb = QuotaCostDBVO.builder()
                    .curCost(curCost.longValue())
                    .appId(quota.getAppId())
                    .metricId(quota.getMetricId())
                    .status(DataStatus.ENABLE)
                    .createTime(operationTime)
                    .updateTime(operationTime)
                    .logDate(lastBaseUpdateTime)
                    .maxQps(null == qpsScore ? 0 : qpsScore.intValue())
                    .nextRefreshDate(curNextRefreshDate)
                    .flag(quota.getFlag() + "|" + SDF.format(curNextRefreshDate))
                    .build();
            quotaCostDBVOs.add(qcdb);
        }
        //处理当前
        quotaCostDBVOs.get(0).setCurCost(curCost.longValue());
        quotaCostDBVOs.get(0).setDayCost(metricQpsService.curDaycost(quota.getAppId(), quota.getMetricId(), lastOperationTime));
        quotaCostDBVOs.get(0).setUpdateTime(operationTime);
        quotaCostDBVOs.get(0).setMaxQps(null == qpsScore ? 0 : qpsScore.intValue());
        QuotaCostModel cur = QuotaCostModel.builder()
                .appId(quota.getAppId())
                .metricId(quota.getMetricId())
                .quota(quota.getQuota())
                .cost(quotaCostDBVOs.get(0).getCurCost())
                .dayCost(quotaCostDBVOs.get(0).getDayCost())
                .dateFlag(quotaCostDBVOs.get(0).getFlag())
                .maxQps(quotaCostDBVOs.get(0).getMaxQps())
                .updateTime(operationTime)
                .syncMills(operationTime.getTime())
                .build();
        quotaCostModels.add(cur);
        //放置下一把的消耗值
        Date realNextRefreshDate = Try.of(() -> {
                    CronExpression cronExpression = CronExpression.parse(quota.getCronExpress());
                    LocalDateTime localDateTime = cronExpression.next(DateUtils.addSeconds(operationTime, 31).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    //noinspection ConstantConditions
                    return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                })
                .get();
        realNextRefreshDate = null == realNextRefreshDate ? Try.of(() -> SDF.parse("2099-12-31 23:59:59")).get() : realNextRefreshDate;
        Date nextBaseUpdateTime = Try.of(() -> SDF.parse(String.format("%s 23:59:59", yyyyMMdd.format(operationTime)))).get();
        QuotaCostDBVO nextCost = QuotaCostDBVO.builder()
                .curCost(0L)
                .appId(quota.getAppId())
                .metricId(quota.getMetricId())
                .status(DataStatus.ENABLE)
                .createTime(operationTime)
                .updateTime(operationTime)
                .logDate(nextBaseUpdateTime)
                .nextRefreshDate(realNextRefreshDate)
                .flag(quota.getFlag() + "|" + SDF.format(realNextRefreshDate))
                .maxQps(0)
                .build();
        QuotaCostModel nextCostModel = QuotaCostModel.builder()
                .appId(quota.getAppId())
                .metricId(quota.getMetricId())
                .quota(quota.getQuota())
                .cost(0L)
                .dateFlag(nextCost.getFlag())
                .maxQps(0)
                .dayCost(0L)
                .updateTime(operationTime)
                .syncMills(operationTime.getTime())
                .build();
        quotaCostModels.add(nextCostModel);
        quotaCostDBVOs.add(nextCost);
        //将可能多的数据置为无效
        if (1 < quotaCostDBVOs.size()) {
            for (int i = 0; i < quotaCostDBVOs.size(); i++) {
                if (0 < i && i < quotaCostDBVOs.size() - 2) {
                    quotaCostDBVOs.get(i).setStatus(DataStatus.DISABLE);
                    quotaCostDBVOs.get(i).setUpdateTime(operationTime);
                }
            }
        }
        quotaCostAutoRepo.saveAll(quotaCostDBVOs);
        //持久化&sync
        syncCosts2Remote(quotaCostModels);
        //刷新redis数据
        Double finalCurCost = curCost;
        Try.run(() -> stringRedisTemplate.opsForZSet().incrementScore(RK_QUOTA_COUNT, costDataValue, -finalCurCost))
                .onFailure(ex -> log.error("refreshQuota fail ! quota:{}", JsonUtils.toJsonString(quota), ex));
        Try.run(() -> stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, qpsDataValue, 0))
                .onFailure(ex -> log.error("refreshQuota fail ! quota:{}", JsonUtils.toJsonString(quota), ex));
        Try.run(() -> stringRedisTemplate.opsForZSet().add(RK_QUOTA_COUNT, costDataLastLogValue, 1D * System.currentTimeMillis()))
                .onFailure(ex -> log.error("refreshQuota fail ! quota:{}", JsonUtils.toJsonString(quota), ex));

    }

}
