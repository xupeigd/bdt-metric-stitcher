package com.quicksand.bigdata.metric.stitcher.services.impls;

import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;

/**
 * StitcherSecService
 *
 * @author page
 * @date 2022/12/27
 */
@Slf4j
public abstract class StitcherSecService {

    @Value("${vars.managemnet.stitcher.id}")
    Integer stitcherId;
    @Value("${vars.managemnet.stitcher.name}")
    String stitcherName;
    @Value("${vars.managemnet.stitcher.token}")
    String stitcherToken;

    protected String buildStitcherSignHeader() {
        //签署调用
        AppRequestVO remoteRequest = AppRequestVO.builder()
                .id(stitcherId)
                .name(stitcherName)
                .params(Collections.emptyList())
                .signMills(System.currentTimeMillis())
                .build();
        remoteRequest.signValue(stitcherToken);
        log.info("build stitcher header sign ! id:{},name:{},token:{},signMills:{},value:【{}】", stitcherId, stitcherName, stitcherToken, remoteRequest.getSignMills(), remoteRequest.toHeaderValue());
        return remoteRequest.toHeaderValue();
    }

}
