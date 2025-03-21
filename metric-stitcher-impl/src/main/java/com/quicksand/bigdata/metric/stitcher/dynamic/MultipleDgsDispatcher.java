package com.quicksand.bigdata.metric.stitcher.dynamic;

import com.quicksand.bigdata.metric.management.apis.models.ExplainAttributesModel;
import com.quicksand.bigdata.metric.management.metric.models.MetricOverviewModel;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.MetricCandidateHandler;
import com.quicksand.bigdata.metric.stitcher.rests.handlers.MetricQueryHandler;
import com.quicksand.bigdata.metric.stitcher.services.PublishMetricService;
import com.quicksand.bigdata.vars.concurrents.TraceFuture;
import com.quicksand.bigdata.vars.util.JsonUtils;
import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FullDynamicTypeDefinations
 *
 * @author page
 * @date 2022/12/26
 */
@Slf4j
@DgsComponent
public class MultipleDgsDispatcher {

    private static final String COMMON_QUERY_TEMPLA = "type Query {\n%s}\n";
    private static final String CANDIDATEDIMENSION_FUNCTION_SCHEMA = "%s(dimensions: String): Rcvp\n";
    private static final String QUERYMETRIC_FUNCTION_SCHEMA = "%s(attributes: Eas): Rmcp\n";

    @Resource
    MetricCandidateHandler metricCandidateHandler;
    @Resource
    PublishMetricService publishMetricService;
    @Resource
    MetricQueryHandler metricQueryHandler;

    @SuppressWarnings("unused")
    @DgsTypeDefinitionRegistry
    public TypeDefinitionRegistry registryTypes() {
        List<MetricOverviewModel> metrics = publishMetricService.getMetrics();
        Set<String> existEnames = new HashSet<>();
        Set<String> existSerialNumbers = new HashSet<>();
        SchemaParser schemaParser = new SchemaParser();
        if (!CollectionUtils.isEmpty(metrics)) {
            //生成公共的
            List<String> virtualRoutes = metrics.stream()
                    .map(v -> String.format(CANDIDATEDIMENSION_FUNCTION_SCHEMA, "cvi" + v.getId()))
                    .collect(Collectors.toList());
            virtualRoutes.addAll(metrics.stream()
                    .map(v -> existEnames.add("cvn" + v.getEnName()) ? String.format(CANDIDATEDIMENSION_FUNCTION_SCHEMA, "cvn" + v.getEnName()) : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            virtualRoutes.addAll(metrics.stream()
                    .map(v -> existSerialNumbers.add("cvs" + v.getSerialNumber()) ? String.format(CANDIDATEDIMENSION_FUNCTION_SCHEMA, "cvs" + v.getSerialNumber()) : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            virtualRoutes.addAll(metrics.stream()
                    .map(v -> String.format(QUERYMETRIC_FUNCTION_SCHEMA, "rqi" + v.getId()))
                    .collect(Collectors.toList()));
            virtualRoutes.addAll(metrics.stream()
                    .map(v -> existEnames.add("rqn" + v.getEnName()) ? String.format(CANDIDATEDIMENSION_FUNCTION_SCHEMA, "rqn" + v.getEnName()) : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            virtualRoutes.addAll(metrics.stream()
                    .map(v -> existSerialNumbers.add("rqs" + v.getSerialNumber()) ? String.format(CANDIDATEDIMENSION_FUNCTION_SCHEMA, "rqs" + v.getSerialNumber()) : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            String completeSchema = String.format(COMMON_QUERY_TEMPLA, StringUtils.collectionToDelimitedString(virtualRoutes, ""));
            log.info("registryTypes schema : {}", completeSchema);
            return schemaParser.parse(completeSchema);
        }
        return schemaParser.parse("type Query {\n}");
    }

    @SuppressWarnings("unused")
    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registryFunctions(GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry registry) {
        List<MetricOverviewModel> metrics = publishMetricService.getMetrics();
        if (!CollectionUtils.isEmpty(metrics)) {
            Set<String> existEnames = new HashSet<>();
            Set<String> existSerialNumbers = new HashSet<>();
            metrics.forEach(v -> {
                codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "cvi" + v.getId()),
                        AsyncDataFetcher.async(dfe -> metricCandidateHandler.candidateDimensions(v.getId(),
                                Arrays.asList(((String) dfe.getArgument("dimensions")).split(","))), TraceFuture.threadPoolTaskExecutor));
                if (existEnames.add("cvn" + v.getEnName())) {
                    //可以改用name方法注册，但是没有必要
                    codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "cvn" + v.getEnName()),
                            AsyncDataFetcher.async(dfe -> metricCandidateHandler.candidateDimensions(v.getId(),
                                    Arrays.asList(((String) dfe.getArgument("dimensions")).split(","))), TraceFuture.threadPoolTaskExecutor));
                }
                if (existEnames.add("cvs" + v.getEnName())) {
                    codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "cvs" + v.getSerialNumber()),
                            AsyncDataFetcher.async(dfe -> metricCandidateHandler.candidateDimensions(v.getId(),
                                    Arrays.asList(((String) dfe.getArgument("dimensions")).split(","))), TraceFuture.threadPoolTaskExecutor));
                }
                codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "rqi" + v.getId()),
                        AsyncDataFetcher.async(dfe -> metricQueryHandler.queryMetric(v.getId(),
                                        (ExplainAttributesModel) Try.of(() -> JsonUtils.transfrom(dfe.getArgument("attributes"), ExplainAttributesModel.class)).getOrNull()),
                                TraceFuture.threadPoolTaskExecutor));
                if (existEnames.add("rqn" + v.getEnName())) {
                    codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "rqn" + v.getEnName()),
                            AsyncDataFetcher.async(dfe -> metricQueryHandler.queryMetric(v.getId(),
                                            (ExplainAttributesModel) Try.of(() -> JsonUtils.transfrom(dfe.getArgument("attributes"), ExplainAttributesModel.class)).getOrNull()),
                                    TraceFuture.threadPoolTaskExecutor));
                }
                if (existEnames.add("rqs" + v.getEnName())) {
                    codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "rqs" + v.getSerialNumber()),
                            AsyncDataFetcher.async(dfe -> metricQueryHandler.queryMetric(v.getId(),
                                            (ExplainAttributesModel) Try.of(() -> JsonUtils.transfrom(dfe.getArgument("attributes"), ExplainAttributesModel.class)).getOrNull()),
                                    TraceFuture.threadPoolTaskExecutor));
                }
            });
        }
        return codeRegistryBuilder;
    }

}
