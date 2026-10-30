package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.Sku;
import org.springframework.stereotype.Repository;

/**
 * 商品仓储。接库后改为 {@code SkuMapper extends BaseMapper<Sku>} 并注入即可。
 */
@Repository
public class SkuRepository extends InMemoryCrudRepository<Sku> {

    public SkuRepository() {
        super(Sku.class);
    }
}
