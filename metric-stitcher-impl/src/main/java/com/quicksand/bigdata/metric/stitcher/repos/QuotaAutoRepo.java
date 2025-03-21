package com.quicksand.bigdata.metric.stitcher.repos;

import com.quicksand.bigdata.metric.stitcher.dbvos.QuotaDBVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * QuotaAutoRepo
 *
 * @author page
 * @date 2022/12/15
 */
@Repository
public interface QuotaAutoRepo
        extends JpaRepository<QuotaDBVO, Integer> {

    List<QuotaDBVO> findAll();

    List<QuotaDBVO> findByAppIdAndMetricIdOrderByUpdateTimeDesc(int appId, int metricId);

    QuotaDBVO findByFlag(String flag);

}
