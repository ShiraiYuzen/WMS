package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.ShortageRecord;
import org.springframework.stereotype.Repository;

/**
 * 缺货记录仓储。
 */
@Repository
public class ShortageRepository extends InMemoryCrudRepository<ShortageRecord> {

    public ShortageRepository() {
        super(ShortageRecord.class);
    }
}
