package com.quicksand.bigdata.metric.stitcher.configurations;

import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaDBVO;
import com.quicksand.bigdata.metric.stitcher.repos.QuotaAutoRepo;
import com.quicksand.bigdata.metric.stitcher.services.QuotaSchedulingConfigurer;
import com.quicksand.bigdata.vars.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * QuotaStartInitTask
 *
 * @author page
 * @date 2022/12/16
 */
@Slf4j
@Component
public class QuotaStartInitTask
        implements CommandLineRunner {

    @Resource
    QuotaAutoRepo quotaAutoRepo;
    @Resource
    QuotaSchedulingConfigurer quotaSchedulingConfigurer;

    @Override
    public void run(String... args) {
        List<QuotaDBVO> workQuotas = quotaAutoRepo.findAll().stream()
                .filter(v -> 1 == v.getAppType())
                .filter(v -> StringUtils.hasText(v.getCronExpress()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(workQuotas)) {
            log.info("workQuotas start initing ! ");
            quotaSchedulingConfigurer.refresh(workQuotas);
            log.info("workQuotas start complete ! workQuotas:{}", JsonUtils.toJsonString(workQuotas));
        }
    }

}
