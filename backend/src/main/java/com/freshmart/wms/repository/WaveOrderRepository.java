package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.WaveOrder;
import org.springframework.stereotype.Repository;

/**
 * 波次订单关联仓储。
 */
@Repository
public class WaveOrderRepository extends InMemoryCrudRepository<WaveOrder> {

    public WaveOrderRepository() {
        super(WaveOrder.class);
    }
}
