package com.quicksand.bigdata.metric.stitcher.configurations;

import com.quicksand.bigdata.vars.security.consts.VarsSecurityConsts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author page
 * @date 2022/11/3
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class WebSecurityConfig {

    @Value("${vars.sec.debugging:false}")
    boolean debuggingEnable;
    @Value("${vars.stitcher.mixed:false}")
    boolean mixedEnable;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ServerHttpSecurity.AuthorizeExchangeSpec authorizeExchangeSpec = debuggingEnable
                ? http.authorizeExchange()
                : http.authorizeExchange()
                .pathMatchers("/swagger**", "/webjars/**", "/monitor/**", "/graphiql**", "/graphql**")
                .hasRole("ADMIN");
        if (!mixedEnable) {
            authorizeExchangeSpec.pathMatchers("/bff/**").hasAnyRole("TAOISM-GOD");
        }
        return authorizeExchangeSpec
                .anyExchange().permitAll()
                .and()
                .addFilterAfter(
                        (exchange, chain) -> {
                            //取header/cookie
                            ServerHttpRequest request = exchange.getRequest();
                            //按量返取
                            List<String> auths = request.getHeaders().get(VarsSecurityConsts.KEY_HEADER_AUTH);
                            auths = null != auths ? new ArrayList<>(auths) : new ArrayList<>();
                            List<HttpCookie> httpCookies = request.getCookies().get(VarsSecurityConsts.KEY_HEADER_AUTH);
                            if (!CollectionUtils.isEmpty(httpCookies)) {
                                auths.addAll(httpCookies
                                        .stream()
                                        .map(HttpCookie::getValue)
                                        .collect(Collectors.toList()));
                            }
                            if (!CollectionUtils.isEmpty(auths)
                                    && auths.stream().anyMatch(StringUtils::hasText)) {
                                String hitAuth = auths.stream().filter(Objects::nonNull).findFirst().orElse("");
                                if (StringUtils.hasText(hitAuth)) {
                                    exchange.getAttributes().put("PTK", hitAuth);
                                }
                            }
                            return chain.filter(exchange);
                        },
                        SecurityWebFiltersOrder.FIRST)
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable()
                .logout().disable()
                .build();
    }

}