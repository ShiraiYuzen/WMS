package com.example.wms.service;

import com.example.wms.dao.OperationLogDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.OperationLog;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogDao logDao;

    public OperationLogService(OperationLogDao logDao) {
        this.logDao = logDao;
    }

    public void record(OperationLog log) {
        try {
            logDao.insert(log);
        } catch (Exception ignore) {
            // 日志写入失败不影响主流程
        }
    }

    public PageResult<OperationLog> list(String module, String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = logDao.count(module, keyword);
        return new PageResult<>(logDao.findPage(module, keyword, (p - 1) * s, s), total);
    }
}
