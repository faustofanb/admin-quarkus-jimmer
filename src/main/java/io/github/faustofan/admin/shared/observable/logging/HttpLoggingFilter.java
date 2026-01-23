package io.github.faustofan.admin.shared.observable.logging;

import io.github.faustofan.admin.shared.observable.ObservableFacade;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quarkus HTTP filter that logs request & response using {@link ObservableFacade}.
 * This filter automatically captures request/response details and forwards them to the
 * unified observable facade, keeping business code clean.
 */
@Provider
@Priority(Priorities.USER) // Execute after user-defined filters
public class HttpLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_TIME_PROP = "observable.startTime";
    private static final String REQUEST_INFO_PROP = "observable.requestInfo";

    @Inject
    ObservableFacade observableFacade;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        long start = System.currentTimeMillis();
        requestContext.setProperty(START_TIME_PROP, start);

        // Read request body (if present) without consuming the stream for downstream handlers
        String body = null;
        if (requestContext.hasEntity()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            requestContext.getEntityStream().transferTo(baos);
            body = baos.toString(StandardCharsets.UTF_8);
            // Reset the stream so the application can still read it
            requestContext.setEntityStream(new ByteArrayInputStream(baos.toByteArray()));
        }

        HttpLogFormatter.HttpRequestInfo requestInfo = HttpLogFormatter.HttpRequestInfo.builder()
                .method(requestContext.getMethod())
                .uri(requestContext.getUriInfo().getRequestUri().toString())
                .clientIp(requestContext.getHeaderString("X-Forwarded-For"))
                .headers(safeHeaders(requestContext.getHeaders()))
                .body(body)
                .startTime(start)
                .build();
        requestContext.setProperty(REQUEST_INFO_PROP, requestInfo);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        long start = (long) requestContext.getProperty(START_TIME_PROP);
        long duration = System.currentTimeMillis() - start;

        HttpLogFormatter.HttpRequestInfo requestInfo = (HttpLogFormatter.HttpRequestInfo) requestContext.getProperty(REQUEST_INFO_PROP);

        // Response body capture is omitted for simplicity; can be added via WriterInterceptor if needed.
        HttpLogFormatter.HttpResponseInfo responseInfo = HttpLogFormatter.HttpResponseInfo.builder()
                .uri(requestInfo.getUri())
                .status(responseContext.getStatus())
                .headers(safeHeaders(responseContext.getHeaders()))
                .body(null) // body omitted
                .durationMs(duration)
                .build();

        if (observableFacade != null) {
            // 记录 HTTP 响应日志
            observableFacade.logHttpResponse(responseInfo);
            
            // 自动收集指标
            String method = requestInfo.getMethod();
            String status = String.valueOf(responseContext.getStatus());
            String path = extractPath(requestInfo.getUri());
            
            Map<String, String> tags = Map.of(
                "method", method,
                "status", status,
                "path", path
            );
            
            // 记录请求计数器
            observableFacade.incrementCounter(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.HttpMetric.REQUEST_TOTAL,
                tags
            );
            
            // 记录请求耗时
            observableFacade.recordTimer(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.HttpMetric.REQUEST_DURATION,
                duration,
                tags
            );
        }
    }
    
    /** Extract path from full URI (remove query params) */
    private String extractPath(String uri) {
        if (uri == null) return "/";
        int queryIndex = uri.indexOf('?');
        int pathStart = uri.indexOf('/', uri.indexOf("://") + 3);
        if (pathStart == -1) return "/";
        String path = queryIndex > 0 ? uri.substring(pathStart, queryIndex) : uri.substring(pathStart);
        return path.isEmpty() ? "/" : path;
    }

    /** Convert JAX-RS MultivaluedMap to a plain Map<String, List<String>> for logging. */
    private static Map<String, List<String>> safeHeaders(MultivaluedMap<String, ?> raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        return raw.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(Object::toString)
                                .collect(Collectors.toList())));
    }
}
