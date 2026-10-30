package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.ReviewRecord;
import org.springframework.stereotype.Repository;

/**
 * 复核记录仓储。
 */
@Repository
public class ReviewRepository extends InMemoryCrudRepository<ReviewRecord> {

    public ReviewRepository() {
        super(ReviewRecord.class);
    }
}
