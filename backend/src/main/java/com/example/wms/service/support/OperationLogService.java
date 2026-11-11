package com.example.wms.service.support;

import com.example.wms.entity.OperationLog;
import com.example.wms.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void log(String bizType, String bizNo, String action, String actionDesc,
                    String operatorNo, String operatorName, String operatorRole, String detail) {
        String t = BizNumbers.now();
        OperationLog row = new OperationLog();
        row.setBizType(bizType);
        row.setBizNo(bizNo);
        row.setAction(action);
        row.setActionDesc(actionDesc);
        row.setOperatorNo(operatorNo);
        row.setOperatorName(operatorName);
        row.setOperatorRole(operatorRole);
        row.setDetail(detail);
        row.setOperateTime(t);
        row.setCreateTime(t);
        operationLogMapper.insert(row);
    }
}
