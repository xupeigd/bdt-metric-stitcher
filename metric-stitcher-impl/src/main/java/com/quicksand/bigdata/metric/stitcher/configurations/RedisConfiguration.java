package com.quicksand.bigdata.metric.stitcher.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * RedisConfiguration
 *
 * @author page
 * @date 2022/11/22
 */
@Configuration
public class RedisConfiguration {

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript qpsLogScript() {
        DefaultRedisScript redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("META-INF/scripts/qpsLog.lua")));
        redisScript.setResultType(List.class);
        return redisScript;
    }

}
