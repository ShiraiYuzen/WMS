package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.Wave;
import org.springframework.stereotype.Repository;

/**
 * 波次仓储。
 */
@Repository
public class WaveRepository extends InMemoryCrudRepository<Wave> {

    public WaveRepository() {
        super(Wave.class);
    }
}
