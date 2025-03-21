package com.quicksand.bigdata.metric.stitcher;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@OpenAPIDefinition(info = @Info(title = "Metric Stitcher",
        version = "0.0.1",
        description = "Documentation APIs v0.0.1-SNAPSHOT"))
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.quicksand.bigdata.metric.stitcher",
        "com.quicksand.bigdata.metric.management.metric.rests",
        "com.quicksand.bigdata.vars.http.advice",
        "com.quicksand.bigdata.vars.feign.advices",
        "com.quicksand.bigdata.vars.security"})
public class StitcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(StitcherApplication.class, args);
    }

}