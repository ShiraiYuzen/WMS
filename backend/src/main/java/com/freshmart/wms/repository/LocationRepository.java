package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.WarehouseLocation;
import org.springframework.stereotype.Repository;

/**
 * 库位仓储。
 */
@Repository
public class LocationRepository extends InMemoryCrudRepository<WarehouseLocation> {

    public LocationRepository() {
        super(WarehouseLocation.class);
    }
}
