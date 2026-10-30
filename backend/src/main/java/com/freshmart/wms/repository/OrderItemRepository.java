package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.OrderItem;
import org.springframework.stereotype.Repository;

/**
 * 订单明细仓储。
 */
@Repository
public class OrderItemRepository extends InMemoryCrudRepository<OrderItem> {

    public OrderItemRepository() {
        super(OrderItem.class);
    }
}
