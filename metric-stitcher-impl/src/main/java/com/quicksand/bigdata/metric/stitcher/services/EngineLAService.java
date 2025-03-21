package com.quicksand.bigdata.metric.stitcher.services;

import com.quicksand.bigdata.metric.management.datasource.models.ClusterInfoModel;
import com.quicksand.bigdata.metric.management.metric.models.ResultSetModel;
import com.quicksand.bigdata.query.consts.JobState;
import com.quicksand.bigdata.query.models.QueryRespModel;
import com.quicksand.bigdata.vars.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * EngineService
 *
 * @author page
 * @date 2022/11/15
 */
public interface EngineLAService {

    static boolean querySuccess(QueryRespModel resp) {
        return null != resp
                && Objects.equals(JobState.Success, resp.getState())
                && null != resp.getResultSet();
    }

    static boolean resultExist(QueryRespModel resp) {
        return querySuccess(resp)
                && (!CollectionUtils.isEmpty(resp.getResultSet().getColumns())
                || !CollectionUtils.isEmpty(resp.getResultSet().getRows()));
    }

    /**
     * 通用查询
     *
     * @param <T>
     * @param clusterInfo 集群信息
     * @param sql         sql
     * @param mapper      映射器
     * @return instnace of T / null
     */
    <T> T commonQuery(ClusterInfo clusterInfo, String sql, Mapper<T> mapper);

    abstract class Mapper<R>
            implements Function<QueryRespModel, R> {

        /**
         * 转换column meta数据
         *
         * @param queryRespModel 查询响应
         * @return list of ResultSetModel.ColumnMetaModel
         */
        public static List<ResultSetModel.ColumnMetaModel> coverColumnMetas(QueryRespModel queryRespModel) {
            return queryRespModel.getResultSet().getColumnMetas()
                    .stream()
                    .map(v -> JsonUtils.transfrom(v, ResultSetModel.ColumnMetaModel.class))
                    .collect(Collectors.toList());
        }

        public abstract R applySuccess(QueryRespModel queryRespModel);

        public R applyDefault(QueryRespModel queryRespModel) {
            return null;
        }

        @Override
        public R apply(QueryRespModel queryRespModel) {
            if (resultExist(queryRespModel)) {
                return applySuccess(queryRespModel);
            }
            return applyDefault(queryRespModel);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    class ClusterInfo {
        Integer id;
        String name;
        String type;
        String address;
        String defaultDatabase;
        String defaultSchema;
        String userName;
        String password;
        String comment;

        public static ClusterInfo from(ClusterInfoModel cim) {
            ClusterInfo clusterInfo = new ClusterInfo();
            BeanUtils.copyProperties(cim, clusterInfo);
            return clusterInfo;
        }

    }

}
