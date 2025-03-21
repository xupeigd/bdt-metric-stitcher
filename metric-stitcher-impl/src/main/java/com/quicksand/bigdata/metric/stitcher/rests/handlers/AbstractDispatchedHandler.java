package com.quicksand.bigdata.metric.stitcher.rests.handlers;

import com.google.gson.Gson;
import com.quicksand.bigdata.metric.stitcher.services.*;
import com.quicksand.bigdata.metric.stitcher.services.impls.StitcherSecService;
import com.quicksand.bigdata.vars.http.JstAppInfo;
import com.quicksand.bigdata.vars.http.JstInfo;
import com.quicksand.bigdata.vars.http.TraceId;
import com.quicksand.bigdata.vars.http.exts.JstAppInfoExtReactive;
import com.quicksand.bigdata.vars.http.exts.JstInfoExtReactive;
import com.quicksand.bigdata.vars.http.exts.TraceIdExtReactive;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.security.consts.VarsSecurityConsts;
import com.quicksand.bigdata.vars.security.vos.AppRequestVO;
import com.quicksand.bigdata.vars.util.HyperAttributes;
import com.quicksand.bigdata.vars.util.JsonUtils;
import io.vavr.control.Try;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractDispatchedHandler
 *
 * @author page
 * @date 2022/11/14
 */
@Slf4j
public abstract class AbstractDispatchedHandler
        extends StitcherSecService
        implements ApplicationContextAware {

    public ApplicationContext applicationContext;
    @Value("${spring.profiles.active}")
    public String activeProfiles;
    @Resource
    public MetricQpsService metricQpsService;
    @Resource
    public ClusterLAService clusterLAService;
    @Resource
    public ExplainLAService explainLAService;
    @Resource
    public EngineLAService engineLAService;
    @Resource
    public MetricQuotaService metricQuotaService;
    @Resource
    public MetricMateService metricMetaService;
    @Getter
    protected Map<String, RestMethod> url2Methods = new HashMap<>();
    @Value("${vars.trace.prefix:RT}")
    String applicationPrefix;
    @Value("${vars.sec.debugging:false}")
    boolean debuggingEnable;

    protected static AppRequestVO fetchFromThreadLocal() {
        return null != HyperAttributes.get(VarsSecurityConsts.KEY_HEADER_APP_AUTH, AppRequestVO.class)
                ? HyperAttributes.get(VarsSecurityConsts.KEY_HEADER_APP_AUTH, AppRequestVO.class)
                : (StringUtils.hasText(JstAppInfo.excavate()) ? Try.of(() -> AppRequestVO.from(JstAppInfo.excavate())).getOrNull() : null);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    Class<?> resloveParameterRequestType(Parameter parameter) {
        return null != parameter.getAnnotation(PathVariable.class) ? PathVariable.class
                : (null != parameter.getAnnotation(RequestParam.class) ? RequestParam.class
                : (null != parameter.getAnnotation(RequestBody.class) ? RequestBody.class
                : (null != parameter.getAnnotation(RequestHeader.class) ? RequestHeader.class : null)));
    }

    public <T extends AbstractDispatchedHandler> T scan(Class<?> restService) {
        Method[] declaredMethods = restService.getDeclaredMethods();
        if (0 < declaredMethods.length) {
            url2Methods = Arrays.stream(declaredMethods).map(declaredMethod -> {
                        RequestMethod[] decalreRequestMethods = null;
                        String[] decalareUrls = null;
                        RequestMapping requestMapping = declaredMethod.getAnnotation(RequestMapping.class);
                        if (null != requestMapping) {
                            decalreRequestMethods = requestMapping.method();
                            decalareUrls = requestMapping.value();
                        }
                        GetMapping getMapping = declaredMethod.getAnnotation(GetMapping.class);
                        if (null != getMapping) {
                            decalreRequestMethods = new RequestMethod[]{RequestMethod.GET};
                            decalareUrls = getMapping.value();
                        }
                        PostMapping postMapping = declaredMethod.getAnnotation(PostMapping.class);
                        if (null != postMapping) {
                            decalreRequestMethods = new RequestMethod[]{RequestMethod.POST};
                            decalareUrls = postMapping.value();
                        }
                        PutMapping putMapping = declaredMethod.getAnnotation(PutMapping.class);
                        if (null != putMapping) {
                            decalreRequestMethods = new RequestMethod[]{RequestMethod.PUT};
                            decalareUrls = putMapping.value();
                        }
                        DeleteMapping deleteMapping = declaredMethod.getAnnotation(DeleteMapping.class);
                        if (null != deleteMapping) {
                            decalreRequestMethods = new RequestMethod[]{RequestMethod.DELETE};
                            decalareUrls = deleteMapping.value();
                        }
                        if ((null == decalreRequestMethods || 0 >= decalreRequestMethods.length)
                                && (null == decalareUrls || 0 >= decalareUrls.length)) {
                            //该方法不需要进行派发
                            return null;
                        }
                        List<ParamInfo> paramInfos = new ArrayList<>();
                        Parameter[] parameters = declaredMethod.getParameters();
                        if (null != parameters && 0 < parameters.length) {
                            List<Parameter> parameterList = Arrays.stream(parameters)
                                    .filter(p -> null != resloveParameterRequestType(p))
                                    .collect(Collectors.toList());
                            for (int i = 0; i < parameterList.size(); i++) {
                                Parameter parameter = parameterList.get(i);
                                paramInfos.add(ParamInfo.builder()
                                        .index(i)
                                        .annotation(resloveParameterRequestType(parameter))
                                        .name(parameterList.get(i).getName())
                                        .type(parameter.getParameterizedType())
                                        .build());
                            }
                        }
                        return new AbstractMap.SimpleEntry<>(String.format("%s_%s", decalreRequestMethods[0], decalareUrls[0]), RestMethod.builder()
                                .method(declaredMethod)
                                .requestMethod(decalreRequestMethods[0])
                                .url(decalareUrls[0])
                                .params(paramInfos)
                                .build());
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        }
        //noinspection unchecked
        return (T) this;
    }

    <T> T transformRequestBody(String jsonStr, ParamInfo paramInfo, Class<T> clazz) {
        return Try.of(() -> JsonUtils.parseTo(jsonStr, clazz)).get();
    }

    protected AppRequestVO usringDebuggingSign(AppRequestVO appRequest) {
        return debuggingEnable && null == appRequest
                ? Try.of(() -> {
                    String signToken = buildStitcherSignHeader();
                    JstAppInfo.make(signToken);
                    return AppRequestVO.from(signToken);
                })
                .getOrNull()
                : appRequest;
    }

    @SneakyThrows
    public Mono<ServerResponse> dispatch(@NotNull ServerRequest request) {
        AppRequestVO appRequestVO = HyperAttributes.get(VarsSecurityConsts.KEY_HEADER_APP_AUTH, AppRequestVO.class);
        String traceId = TraceIdExtReactive.extendOrMakeRandom(request, this.applicationPrefix);
        String jstInfo = JstInfoExtReactive.extendFromRequest(request);
        String jstAppInfo = JstAppInfoExtReactive.extendFromRequest(request);
        MDC.put("costTime", "0");
        try {
            HttpMethod requestMethod = request.method();
            String requestPath = request.requestPath().toString();
            Optional<Object> matchPattern = request.attribute("org.springframework.web.reactive.HandlerMapping.bestMatchingPattern");
            if (null == requestMethod || !matchPattern.isPresent()) {
                return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(routeNotRegsiter(requestPath, null != requestMethod ? requestMethod : HttpMethod.TRACE)));
            }
            String requestKey = String.format("%s_%s", requestMethod, ((PathPattern) matchPattern.get()).getPatternString());
            if (null == url2Methods.get(requestKey)) {
                return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(routeNotRegsiter(requestPath, requestMethod)));
            }
            RestMethod restMethod = url2Methods.get(requestKey);
            if (CollectionUtils.isEmpty(restMethod.getParams())) {
                //无参调用
                return Try.of(() -> ServerResponse.status(HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(restMethod.getMethod().invoke(this))))
                        .onFailure(ex -> log.error("dispatch without params error ! ", ex))
                        .getOrElse(() -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(
                                        Response.response(HttpStatus.INTERNAL_SERVER_ERROR, "dispatch without params error !"))));
            }
            //读取参数，反向调用
            Object[] params = new Object[restMethod.getParams().size()];
            ParamInfo requestBodyParam = null;
            for (int i = 0; i < restMethod.getParams().size(); i++) {
                ParamInfo paramInfo = restMethod.getParams().get(i);
                if (Objects.equals(RequestBody.class, paramInfo.getAnnotation())) {
                    requestBodyParam = paramInfo;
                    continue;
                }
                params[i] = analysisParam(paramInfo, request);
            }
            if (null == requestBodyParam) {
                return Try.of(() -> ServerResponse.status(HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(restMethod.getMethod().invoke(this, params))))
                        .onFailure(ex -> log.error(String.format("dispatch with params error ! params:%s", StringUtils.arrayToCommaDelimitedString(params)), ex))
                        .getOrElse(() -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(
                                        Response.response(HttpStatus.INTERNAL_SERVER_ERROR, "dispatch without params error !"))));
            } else {
                ParamInfo finalRequestBodyParam = requestBodyParam;
                return request.bodyToMono(String.class)
                        .map(s -> transformRequestBody(s, finalRequestBodyParam, Try.of(() -> Class.forName(finalRequestBodyParam.getType().getTypeName())).get()))
                        .flatMap(v -> {
                            HyperAttributes.put(VarsSecurityConsts.KEY_HEADER_APP_AUTH, appRequestVO, AppRequestVO.class);
                            TraceId.make(traceId);
                            JstInfo.make(jstInfo);
                            JstAppInfo.make(jstAppInfo);
                            params[finalRequestBodyParam.getIndex()] = v;
                            return Try.of(() -> ServerResponse.status(HttpStatus.OK)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(BodyInserters.fromValue(restMethod.getMethod().invoke(this, params))))
                                    .onFailure(ex -> log.error("reflect invoke fail ! ", ex))
                                    .getOrElse(() -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(BodyInserters.fromValue(routeNotRegsiter(requestPath, requestMethod))));
                        });
            }
        } catch (Exception e) {
            throw e;
        } finally {
            TraceId.destory();
            JstInfo.destory();
            JstAppInfo.destory();
            HyperAttributes.clear();
            MDC.clear();
        }
    }

    //若入参类型无法在通用进行处理，可以在子类进行重写覆盖
    public Object analysisParam(ParamInfo paramInfo, ServerRequest request) throws ClassNotFoundException {
        Class<?> annotation = paramInfo.getAnnotation();
        String objStr = Objects.equals(RequestParam.class, annotation) ? request.queryParam(paramInfo.name).orElse(null) :
                (Objects.equals(RequestHeader.class, annotation) ? request.headers().firstHeader(paramInfo.name)
                        : Objects.equals(PathVariable.class, annotation) ? request.pathVariable(paramInfo.name) : null);
        if (null == objStr) {
            return null;
        }
        //复合范型对象(复合范型有范型再范型的情况)
        if (paramInfo.type instanceof ParameterizedType) {
            //是否集合
            ParameterizedType type = (ParameterizedType) paramInfo.type;
            Class rawType = Try.of(() -> Class.forName(type.getRawType().getTypeName())).getOrNull();
            if (null == rawType) {
                throw new ClassNotFoundException(type.getRawType().getTypeName() + " not found !");
            }
            if (Objects.equals(rawType, List.class)) {
                objStr = String.format("[%s]", objStr);
            }
        }
        return new Gson().fromJson(objStr, paramInfo.type);
    }

    Response<Map<String, String>> routeNotRegsiter(String path, HttpMethod requestMethod) {
        Map<String, String> dataMap = new TreeMap<>();
        dataMap.put("path", path);
        dataMap.put("status", String.valueOf(HttpStatus.NOT_FOUND.value()));
        dataMap.put("method", requestMethod.name());
        dataMap.put("time", new Date().toString());
        Response<Map<String, String>> response = Response.response(dataMap, HttpStatus.NOT_FOUND);
        response.setMsg("route not register ! ");
        return response;
    }

    @SuppressWarnings("NullableProblems")
    protected RouterFunction<ServerResponse> routes(AbstractDispatchedHandler handler) {
        //读取路由，生成router
        List<RouterFunction<ServerResponse>> routerFunctions = handler.getUrl2Methods()
                .values().stream()
                .map(restMethod -> {
                    RequestMethod requestMethod = restMethod.getRequestMethod();
                    switch (requestMethod) {
                        case GET:
                            return RouterFunctions.route(RequestPredicates.GET(restMethod.getUrl()), handler::dispatch);
                        case POST:
                            return RouterFunctions.route(RequestPredicates.POST(restMethod.getUrl()).and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), handler::dispatch);
                        case PUT:
                            return RouterFunctions.route(RequestPredicates.PUT(restMethod.getUrl()).and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), handler::dispatch);
                        case DELETE:
                            return RouterFunctions.route(RequestPredicates.DELETE(restMethod.getUrl()), handler::dispatch);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(routerFunctions)) {
            if (1 == routerFunctions.size()) {
                return routerFunctions.get(0);
            } else {
                RouterFunction<ServerResponse> rootFunction = routerFunctions.get(0);
                for (RouterFunction<ServerResponse> routerFunction : routerFunctions) {
                    if (!Objects.equals(rootFunction, routerFunction)) {
                        rootFunction = rootFunction.and(routerFunction);
                    }
                }
                return rootFunction;
            }
        }
        return null;
    }

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParamInfo {

        /**
         * 参数索引
         */
        int index;

        /**
         * 名称
         */
        String name;

        /**
         * 获取注解
         */
        Class<?> annotation;

        /**
         * 参数类型
         */
        Type type;

    }

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RestMethod {

        /**
         * URL
         */
        String url;

        /**
         * 方法 GET/POST/PUT/DELETE
         */
        RequestMethod requestMethod;

        /**
         * 实际method
         */
        Method method;

        /**
         * 参数
         */
        @SuppressWarnings("rawtypes")
        List<ParamInfo> params;

    }

}
