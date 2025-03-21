package com.quicksand.bigdata.metric.stitcher.utils;

import com.quicksand.bigdata.vars.security.consts.VarsSecurityConsts;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RequestAuthResolver
 *
 * @author page
 * @date 2022/10/27
 */
public interface RequestAppResolver {

    String FLAG_AUTH = VarsSecurityConsts.KEY_HEADER_APP_AUTH;

    static String getFromHeader(ServerHttpRequest request) {
        List<String> auths = request.getHeaders().get(FLAG_AUTH);
        return CollectionUtils.isEmpty(auths) || auths.stream().noneMatch(StringUtils::hasText)
                ? ""
                : auths.stream().filter(StringUtils::hasText).findFirst().orElse("");
    }

    static String resolveAppInfo(ServerHttpRequest request) {
        return getFromHeader(request);
    }

}
