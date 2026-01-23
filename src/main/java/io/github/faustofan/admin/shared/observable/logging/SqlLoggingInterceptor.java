package io.github.faustofan.admin.shared.observable.logging;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.P6Logger;
import io.github.faustofan.admin.shared.observable.ObservableFacade;
import io.github.faustofan.admin.shared.observable.logging.SqlLogFormatter.SqlInfo;
import jakarta.enterprise.inject.spi.CDI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 p6spy Logger，复用现有的 {@link SqlLogFormatter} 进行多行美化日志输出。
 * <p>
 * p6spy 会在每次 JDBC 调用时自动调用此类，我们将 SQL 信息转发给 {@link ObservableFacade}，
 * 从而统一所有 SQL 日志（包括 Jimmer、Hibernate、原生 JDBC 等）。
 * <p>
 * 特性：
 * - SQL 参数已内联（p6spy 已替换 ? 为实际值）
 * - 捕获数据源连接信息（支持多数据源识别）
 * - 捕获调用栈前 4 层（排除 p6spy 框架本身）
 */
public class SqlLoggingInterceptor implements P6Logger {

    private static final int STACKTRACE_DEPTH = 4;

    /**
     * p6spy 会调用此方法记录每条 SQL
     *
     * @param connectionId 连接 ID
     * @param now          当前时间字符串
     * @param elapsed      执行耗时（毫秒）
     * @param category     SQL 分类（statement, commit, rollback 等）
     * @param prepared     预编译 SQL（带 ?）
     * @param sql          实际执行的 SQL（已替换参数，即内联）
     * @param url          数据源连接 URL
     */
    @Override
    public void logSQL(int connectionId, String now, long elapsed, Category category,
                       String prepared, String sql, String url) {
        // 只记录 statement、batch、commit 等有意义的 SQL，过滤掉 resultset 等噪音
        if (category == Category.STATEMENT || category == Category.BATCH || category == Category.COMMIT) {
            try {
                ObservableFacade facade = CDI.current().select(ObservableFacade.class).get();
                
                // 捕获调用栈（排除 p6spy 和本类的栈帧）
                List<String> stackTrace = captureStackTrace();
                
                // 构造 SqlInfo
                // 注意：sql 参数已经是内联后的（p6spy 已把 ? 替换为实际值）
                SqlInfo sqlInfo = SqlInfo.builder()
                        .sql(sql)                              // 内联后的 SQL
                        .parameters(Collections.emptyList())   // p6spy 不提供原始参数，但已内联到 sql 中
                        .durationMs(elapsed)
                        .rowCount(-1)                          // p6spy 不提供受影响行数
                        .connectionUrl(url)                    // 数据源 URL（多数据源识别）
                        .connectionId(connectionId)            // 连接 ID
                        .stackTrace(stackTrace)                // 调用栈前 4 层
                        .build();
                
                facade.logSql(sqlInfo);
            } catch (Exception e) {
                // 避免日志记录失败影响业务
                System.err.println("[SQL Logger Error] " + e.getMessage());
            }
        }
    }

    @Override
    public void logException(Exception e) {
        // SQL 异常记录（可选）
        try {
            ObservableFacade facade = CDI.current().select(ObservableFacade.class).get();
            SqlInfo sqlInfo = SqlInfo.builder()
                    .sql("[SQL Exception]")
                    .parameters(Collections.emptyList())
                    .durationMs(0)
                    .error(e)
                    .stackTrace(captureStackTrace())
                    .build();
            facade.logSql(sqlInfo);
        } catch (Exception ex) {
            System.err.println("[SQL Exception Logger Error] " + ex.getMessage());
        }
    }

    @Override
    public void logText(String text) {
        // 不使用此方法
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        // 只启用 statement、batch、commit 类别
        return category == Category.STATEMENT || 
               category == Category.BATCH || 
               category == Category.COMMIT;
    }
    
    /**
     * 捕获当前调用栈的前 N 层（排除 p6spy 和本类的栈帧）
     */
    private List<String> captureStackTrace() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        List<String> result = new ArrayList<>();
        int count = 0;
        
        for (StackTraceElement element : elements) {
            String className = element.getClassName();
            
            // 跳过 Thread、p6spy、本类以及 JDK 内部类
            if (className.startsWith("java.lang.Thread") ||
                className.startsWith("com.p6spy") ||
                className.startsWith("io.github.faustofan.admin.shared.observable.logging.SqlLoggingInterceptor") ||
                className.startsWith("sun.") ||
                className.startsWith("jdk.internal.")) {
                continue;
            }
            
            // 格式化：类名.方法名(文件名:行号)
            String formatted = String.format("%s.%s(%s:%d)",
                    simplifyClassName(className),
                    element.getMethodName(),
                    element.getFileName(),
                    element.getLineNumber());
            
            result.add(formatted);
            count++;
            
            if (count >= STACKTRACE_DEPTH) {
                break;
            }
        }
        
        return result;
    }
    
    /**
     * 简化类名（只保留最后两段）
     */
    private String simplifyClassName(String fullClassName) {
        String[] parts = fullClassName.split("\\.");
        if (parts.length <= 2) {
            return fullClassName;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
