package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.ReviewDiffItem;
import org.springframework.stereotype.Repository;

/**
 * 复核差异明细仓储。
 */
@Repository
public class ReviewDiffRepository extends InMemoryCrudRepository<ReviewDiffItem> {

    public ReviewDiffRepository() {
        super(ReviewDiffItem.class);
    }
}
