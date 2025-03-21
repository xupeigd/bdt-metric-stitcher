package com.quicksand.bigdata.metric.stitcher.configurations;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxl-job config
 *
 * @author xuxueli 2017-04-28
 */
@ConditionalOnProperty(value = "metric.jobs.enable", havingValue = "true")
@Slf4j
@Configuration
public class MetricJobsConfig {

    @Value("${metric.jobs.admin.addresses}")
    String adminAddresses;
    @Value("${metric.jobs.accessToken}")
    String accessToken;
    @Value("${metric.jobs.executor.appname}")
    String appname;
    @Value("${metric.jobs.executor.address}")
    String address;
    @Value("${metric.jobs.executor.ip}")
    String ip;
    @Value("${metric.jobs.executor.port}")
    int port;
    @Value("${metric.jobs.executor.logpath}")
    String logPath;
    @Value("${metric.jobs.executor.logretentiondays}")
    int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor metricJobsExecutor() {
        log.info(">>>>>>>>>>> metric-jobs config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        return xxlJobSpringExecutor;
    }

}