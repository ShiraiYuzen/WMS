package com.example.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wms.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
