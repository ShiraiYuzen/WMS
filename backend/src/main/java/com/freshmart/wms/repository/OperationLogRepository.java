package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.OperationLog;
import org.springframework.stereotype.Repository;

/**
 * 操作日志仓储。
 */
@Repository
public class OperationLogRepository extends InMemoryCrudRepository<OperationLog> {

    public OperationLogRepository() {
        super(OperationLog.class);
    }
}
