package io.github.faustofan.admin.shared.security.exception;

import io.github.faustofan.admin.shared.security.constants.SecurityErrorCode;
import io.github.faustofan.admin.shared.web.dtos.ApiResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * 安全异常映射器
 * <p>
 * 将安全相关的异常转换为统一的HTTP响应格式。
 */
@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    private static final Logger LOG = Logger.getLogger(SecurityExceptionMapper.class);

    @Override
    public Response toResponse(SecurityException exception) {
        SecurityErrorCode errorCode = exception.getErrorCode();
        int httpStatus = mapToHttpStatus(errorCode);

        LOG.warnv("Security exception: code={0}, message={1}",
            errorCode.getCode(), exception.getMessage());

        String codeStr = errorCode.getCode();
        // 将字符串错误码转为整数（取最后3位数字）
        int numericCode;
        try {
            numericCode = Integer.parseInt(codeStr.substring(1));
        } catch (Exception e) {
            numericCode = 9999;
        }

        ApiResponse<Void> response = ApiResponse.error(numericCode, errorCode.getMessage());

        return Response.status(httpStatus)
            .entity(response)
            .build();
    }

    /**
     * 根据错误码映射HTTP状态码
     */
    private int mapToHttpStatus(SecurityErrorCode errorCode) {
        String code = errorCode.getCode();
        // A开头：认证相关，返回401 Unauthorized
        if (code.startsWith("A")) {
            return Response.Status.UNAUTHORIZED.getStatusCode();
        }
        // T开头：租户相关，返回403 Forbidden
        if (code.startsWith("T")) {
            return Response.Status.FORBIDDEN.getStatusCode();
        }
        // Z开头：授权相关，返回403 Forbidden
        if (code.startsWith("Z")) {
            return Response.Status.FORBIDDEN.getStatusCode();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }
}
