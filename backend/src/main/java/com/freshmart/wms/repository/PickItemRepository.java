package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.PickItem;
import org.springframework.stereotype.Repository;

/**
 * 拣货明细仓储。
 */
@Repository
public class PickItemRepository extends InMemoryCrudRepository<PickItem> {

    public PickItemRepository() {
        super(PickItem.class);
    }
}
