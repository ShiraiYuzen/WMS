package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.Employee;
import org.springframework.stereotype.Repository;

/**
 * 员工仓储。
 */
@Repository
public class EmployeeRepository extends InMemoryCrudRepository<Employee> {

    public EmployeeRepository() {
        super(Employee.class);
    }
}
