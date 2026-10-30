package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.OrderInfo;
import org.springframework.stereotype.Repository;

/**
 * 订单仓储。
 */
@Repository
public class OrderRepository extends InMemoryCrudRepository<OrderInfo> {

    public OrderRepository() {
        super(OrderInfo.class);
    }
}
