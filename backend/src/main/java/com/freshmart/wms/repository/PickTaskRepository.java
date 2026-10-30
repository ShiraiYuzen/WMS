package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.PickTask;
import org.springframework.stereotype.Repository;

/**
 * 拣货任务仓储。
 */
@Repository
public class PickTaskRepository extends InMemoryCrudRepository<PickTask> {

    public PickTaskRepository() {
        super(PickTask.class);
    }
}
