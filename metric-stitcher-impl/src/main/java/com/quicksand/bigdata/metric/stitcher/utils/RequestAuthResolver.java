package com.quicksand.bigdata.metric.stitcher.utils;

import com.quicksand.bigdata.vars.security.consts.VarsSecurityConsts;
import org.springframework.http.HttpCookie;
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
public interface RequestAuthResolver {

    String FLAG_AUTH = VarsSecurityConsts.KEY_HEADER_AUTH;

    static String getFromHeader(ServerHttpRequest request) {
        List<String> auths = request.getHeaders().get(FLAG_AUTH);
        return CollectionUtils.isEmpty(auths) || auths.stream().noneMatch(StringUtils::hasText)
                ? ""
                : auths.stream().filter(StringUtils::hasText).findFirst().orElse("");
    }

    static String getFromCookie(ServerHttpRequest request) {
        List<HttpCookie> auths = request.getCookies().get(FLAG_AUTH);
        return CollectionUtils.isEmpty(auths) || auths.stream().noneMatch(v -> StringUtils.hasText(v.getValue()))
                ? ""
                : auths.stream().filter(v -> StringUtils.hasText(v.getValue())).findFirst()
                .map(HttpCookie::getValue).orElse("");
    }


    static String resolveAuthInfo(ServerHttpRequest request) {
        String headerAuth = getFromHeader(request);
        return StringUtils.hasText(headerAuth) ? headerAuth : getFromCookie(request);
    }

}
