package com.quicksand.bigdata.metric.stitcher.filters;


import com.quicksand.bigdata.metric.stitcher.utils.RequestAppResolver;
import com.quicksand.bigdata.metric.stitcher.utils.RequestAuthResolver;
import com.quicksand.bigdata.vars.security.consts.VarsSecurityConsts;
import com.quicksand.bigdata.vars.security.service.VarsSecurityPersistenceService;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import com.quicksand.bigdata.vars.security.vos.UserSecurityDetails;
import com.quicksand.bigdata.vars.util.HyperAttributes;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * AuthTransformFilter
 *
 * @author page
 * @date 2022/11/22
 */
@Slf4j
@Component
public class AuthTransformFilter
        implements WebFilter, Ordered {

    @Resource
    VarsSecurityPersistenceService varsSecurityPersistenceService;

    @Override
    public int getOrder() {
        return 0;
    }

    private void tryTransform(ServerWebExchange exchange) {
        Try.run(() -> {
            String authInfoStr = RequestAuthResolver.resolveAuthInfo(exchange.getRequest());
            if (StringUtils.hasText(authInfoStr)) {
                UserSecurityDetails userSecurityDetails = varsSecurityPersistenceService.loadUserDetails(authInfoStr);
                if (null != userSecurityDetails) {
                    HyperAttributes.put(VarsSecurityConsts.KEY_HEADER_AUTH, userSecurityDetails, UserSecurityDetails.class);
                }
            }
        });
        Try.run(() -> {
            String curAppStr = RequestAppResolver.resolveAppInfo(exchange.getRequest());
            AppRequestVO appRequest = StringUtils.hasText(curAppStr) ? AppRequestVO.from(curAppStr) : null;
            if (null != appRequest) {
                HyperAttributes.put(VarsSecurityConsts.KEY_HEADER_APP_AUTH, appRequest, AppRequestVO.class);
            }
        });
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        //尝试转换即可
        tryTransform(exchange);
        return chain.filter(exchange)
                .doFinally(s -> {
                    HyperAttributes.remove(VarsSecurityConsts.KEY_HEADER_AUTH);
                    HyperAttributes.remove(VarsSecurityConsts.KEY_HEADER_APP_AUTH);
                });
    }

}
