package com.hr.ai.service.texttosql;

/**
 * SQL 执行失败异常，携带原始错误信息供自纠错流程使用。
 */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
