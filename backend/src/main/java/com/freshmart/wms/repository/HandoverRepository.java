package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.HandoverRecord;
import org.springframework.stereotype.Repository;

/**
 * 出库交接仓储。
 */
@Repository
public class HandoverRepository extends InMemoryCrudRepository<HandoverRecord> {

    public HandoverRepository() {
        super(HandoverRecord.class);
    }
}
