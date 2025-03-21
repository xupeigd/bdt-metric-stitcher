package com.quicksand.bigdata.metric.stitcher.annotations.ext.dgs.core;

import com.quicksand.bigdata.metric.management.metric.models.CandidateValuePairModel;
import com.quicksand.bigdata.vars.http.model.Response;
import com.quicksand.bigdata.vars.util.JsonUtils;
import io.vavr.control.Try;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ResponseModelUtils
 *
 * @author page
 * @date 2022/12/26
 */
public final class ResponseModelUtils {

    private ResponseModelUtils() {
    }

    private static String distinguish(Field v) {
        return distinguish(v.getType(), v.getName());
    }

    private static String distinguish(Class<?> vClass, String vName) {
        if (Objects.equals(vClass, Integer.class)
                || Objects.equals(vClass, int.class)
                || Objects.equals(vClass, Short.class)
                || Objects.equals(vClass, short.class)
                || Objects.equals(vClass, Long.class)
                || Objects.equals(vClass, long.class)) {
            return vName + ": Int";
        } else if (Objects.equals(vClass, Float.class)
                || Objects.equals(vClass, float.class)
                || Objects.equals(vClass, Double.class)
                || Objects.equals(vClass, double.class)) {
            return vName + ": Float";
        } else if (Objects.equals(vClass, Boolean.class)
                || Objects.equals(vClass, boolean.class)) {
            return vName + ": Boolean";
        } else if (Objects.equals(vClass, String.class)) {
            return vName + ": String";
        } else if (Objects.equals(vClass, Object.class)
                || Objects.equals(vClass, List.class)
                || Objects.equals(vClass, Set.class)) {
            return vName + ": {}";
        }
        return "";
    }

    public static <T> String cover2SchemaStr(T t, List<String> preixStrs) {
        Class<?> tClass = t.getClass();
        List<String> collect = Arrays.stream(tClass.getDeclaredFields())
                .filter(v -> Modifier.STATIC > v.getModifiers())
                .map(v -> {
                    String distinguish = distinguish(v);
                    if (distinguish.endsWith("{}")) {
                        v.setAccessible(true);
                        Object o = Try.of(() -> v.get(t)).get();
                        if (null == o) {
                            distinguish = "";
                        } else {
                            if (o instanceof Collection) {
                                @SuppressWarnings("rawtypes") Collection oc = (Collection) o;
                                if (CollectionUtils.isEmpty(oc)) {
                                    //无法识别的类型是无法转换的
                                    distinguish = "";
                                } else {
                                    String soName = "";
                                    for (Object so : oc) {
                                        if (null != so) {
                                            soName = so.getClass().getSimpleName();
                                            if (!StringUtils.hasText(distinguish(so.getClass(), soName))) {
                                                //判断基础类型
                                                String subSchema = "type " + cover2SchemaStr(so, preixStrs);
                                                if (!preixStrs.contains(subSchema)) {
                                                    preixStrs.add(subSchema);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    distinguish = StringUtils.hasText(soName) ? String.format("%s:[%s]", v.getName(), soName) : "";
                                }
                            } else {
                                distinguish = String.format("%s:{\n%s\n}", v.getName(), cover2SchemaStr(o, preixStrs));
                            }
                        }
                    }
                    return distinguish;
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return String.format(" %s {\n%s\n}\n", tClass.getSimpleName() + (preixStrs.isEmpty() ? "" : "_" + DigestUtils.md5DigestAsHex(JsonUtils.toJsonString(preixStrs).getBytes(StandardCharsets.UTF_8)).substring(0, 8)),
                StringUtils.collectionToDelimitedString(collect, "\n"));
    }

    public static void main(String[] args) {
        Response<List<CandidateValuePairModel>> resp = Response.ok(
                Arrays.asList(
                        CandidateValuePairModel.builder()
                                .name("example")
                                .values(Arrays.asList("val0", "val1", "val2"))
                                .build()));
        List<String> objStrs = new ArrayList<>();
        String schema = cover2SchemaStr(resp, objStrs);
        for (String objStr : objStrs) {
            System.out.println(objStr);
        }
        System.out.println("type " + schema);
        System.out.println("-->");
    }

}
