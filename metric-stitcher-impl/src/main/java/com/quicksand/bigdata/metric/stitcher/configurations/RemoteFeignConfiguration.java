package com.quicksand.bigdata.metric.stitcher.configurations;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * @author page
 * @date 2022/11/3
 */
@EnableFeignClients(basePackages = {
        "com.quicksand.bigdata.metric.management.metric.rests",
        "com.quicksand.bigdata.query.rests",
        "com.quicksand.bigdata.metric.management.apis.rest"})
@Configuration
public class RemoteFeignConfiguration {

}
