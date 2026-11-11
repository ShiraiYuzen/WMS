package com.example.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.OperationLog;
import com.example.wms.mapper.OperationLogMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public LogService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> trace(String bizNo) {
        List<OperationLog> list = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getBizNo, bizNo)
                .orderByDesc(OperationLog::getOperateTime)
                .orderByDesc(OperationLog::getId));
        return toMaps(list);
    }

    public PageResult<Map<String, Object>> page(String bizType, String bizNo, String operatorNo,
                                                int pageNum, int pageSize) {
        LambdaQueryWrapper<OperationLog> q = new LambdaQueryWrapper<>();
        if (notBlank(bizType)) {
            q.eq(OperationLog::getBizType, bizType);
        }
        if (notBlank(bizNo)) {
            q.like(OperationLog::getBizNo, bizNo.trim());
        }
        if (notBlank(operatorNo)) {
            q.eq(OperationLog::getOperatorNo, operatorNo);
        }
        q.orderByDesc(OperationLog::getOperateTime).orderByDesc(OperationLog::getId);
        Page<OperationLog> page = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), q);
        return new PageResult<>(toMaps(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    private List<Map<String, Object>> toMaps(List<OperationLog> list) {
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (OperationLog row : list) {
            rows.add(objectMapper.convertValue(row, new TypeReference<LinkedHashMap<String, Object>>() {}));
        }
        return rows;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
