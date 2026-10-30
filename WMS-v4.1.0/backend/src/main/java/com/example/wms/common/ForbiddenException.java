package com.example.wms.common;

/**
 * 越权访问（HTTP 403）。例如拣货员操作他人任务、非本人单据。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
