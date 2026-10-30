package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.Carrier;
import org.springframework.stereotype.Repository;

/**
 * 承运商仓储。
 */
@Repository
public class CarrierRepository extends InMemoryCrudRepository<Carrier> {

    public CarrierRepository() {
        super(Carrier.class);
    }
}
