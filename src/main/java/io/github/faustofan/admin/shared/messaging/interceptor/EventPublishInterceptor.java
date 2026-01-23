package io.github.faustofan.admin.shared.messaging.interceptor;

import io.github.faustofan.admin.shared.messaging.annotation.EventPublish;
import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;
import io.github.faustofan.admin.shared.messaging.core.EventBus;
import io.github.faustofan.admin.shared.messaging.facade.MessagingFacade;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事件发布拦截器
 * <p>
 * 处理 {@link EventPublish} 注解，在方法执行后自动发布事件
 */
@EventPublish(topic = "")  // Default binding
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class EventPublishInterceptor {

    private static final Logger LOG = Logger.getLogger(EventPublishInterceptor.class);

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#(\\w+)(\\.\\w+)*");

    @Inject
    MessagingFacade messagingFacade;

    @Inject
    MessagingConfig config;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        EventPublish annotation = getAnnotation(context);
        if (annotation == null || !config.enabled()) {
            return context.proceed();
        }

        // 方法执行前发布
        if (annotation.beforeInvocation()) {
            publishEvent(annotation, context, null);
        }

        // 执行方法
        Object result = context.proceed();

        // 方法执行后发布（默认）
        if (!annotation.beforeInvocation()) {
            // 检查发布条件
            if (checkCondition(annotation.condition(), context, result)) {
                publishEvent(annotation, context, result);
            }
        }

        return result;
    }

    /**
     * 发布事件
     */
    private void publishEvent(EventPublish annotation, InvocationContext context, Object result) {
        try {
            // 构建 payload
            Object payload = resolvePayload(annotation, context, result);

            // 构建事件
            String source = resolveSource(annotation, context);
            EventType eventType = resolveEventType(annotation.eventType());

            // 使用 DomainEvent.of 工厂方法
            DomainEvent<Object> event = DomainEvent.of(
                source,                    // aggregateId
                annotation.topic(),        // aggregateType
                eventType,
                payload
            );

            // 获取通道
            ChannelType channelType = annotation.channel();
            if (channelType == ChannelType.AUTO) {
                channelType = config.channel();
            }

            EventBus eventBus = messagingFacade.getEventBus(channelType);

            // 根据配置选择发布方式
            if (annotation.async()) {
                if (annotation.deliveryMode() == DeliveryMode.FIRE_AND_FORGET) {
                    eventBus.fire(event);
                } else {
                    eventBus.publishAsync(event)
                        .whenComplete((v, ex) -> {
                            if (ex != null) {
                                LOG.warnv("Async event publish failed: {0}", ex.getMessage());
                            } else {
                                LOG.debugv("Async event published: {0}", annotation.topic());
                            }
                        });
                }
            } else {
                eventBus.publish(event);
            }

            LOG.debugv("Event published to topic: {0}, eventType: {1}", 
                annotation.topic(), annotation.eventType());

        } catch (Exception e) {
            LOG.errorv("Failed to publish event: {0}", e.getMessage());
            if (annotation.throwOnFailure()) {
                throw new RuntimeException("Event publish failed", e);
            }
        }
    }

    /**
     * 获取注解
     */
    private EventPublish getAnnotation(InvocationContext context) {
        EventPublish annotation = context.getMethod().getAnnotation(EventPublish.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(EventPublish.class);
        }
        return annotation;
    }

    /**
     * 解析 payload
     */
    private Object resolvePayload(EventPublish annotation, InvocationContext context, Object result) {
        String payloadExpr = annotation.payload();
        
        if (payloadExpr == null || payloadExpr.isEmpty()) {
            // 默认使用返回值
            return result;
        }

        // 解析表达式
        Map<String, Object> variables = buildVariableMap(context, result);
        return evaluateExpression(payloadExpr, variables);
    }

    /**
     * 解析事件来源
     */
    private String resolveSource(EventPublish annotation, InvocationContext context) {
        if (annotation.source() != null && !annotation.source().isEmpty()) {
            return annotation.source();
        }
        return context.getTarget().getClass().getSimpleName() + "." + context.getMethod().getName();
    }

    /**
     * 解析事件类型
     */
    private EventType resolveEventType(String typeStr) {
        try {
            return EventType.fromCode(typeStr);
        } catch (Exception e) {
            return EventType.CUSTOM;
        }
    }

    /**
     * 检查发布条件
     */
    private boolean checkCondition(String condition, InvocationContext context, Object result) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        Map<String, Object> variables = buildVariableMap(context, result);
        return evaluateCondition(condition, variables);
    }

    /**
     * 构建变量映射
     */
    private Map<String, Object> buildVariableMap(InvocationContext context, Object result) {
        Map<String, Object> variables = new HashMap<>();

        Method method = context.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = context.getParameters();

        if (parameters != null && args != null) {
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                variables.put(parameters[i].getName(), args[i]);
                variables.put("p" + i, args[i]);
            }
        }

        if (result != null) {
            variables.put("result", result);
        }

        return variables;
    }

    /**
     * 表达式求值
     */
    private Object evaluateExpression(String expression, Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String propertyPath = matcher.group(2);

            Object value = variables.get(varName);
            if (value != null && propertyPath != null && !propertyPath.isEmpty()) {
                value = resolveProperty(value, propertyPath.substring(1));
            }
            return value;
        }
        return expression;
    }

    /**
     * 条件表达式求值
     */
    private boolean evaluateCondition(String expression, Map<String, Object> variables) {
        try {
            String evaluatedExpr = expression;
            
            Matcher matcher = VARIABLE_PATTERN.matcher(expression);
            while (matcher.find()) {
                String fullMatch = matcher.group();
                String varName = matcher.group(1);
                Object value = variables.get(varName);

                if (evaluatedExpr.contains(fullMatch + " == true")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " == true", 
                        String.valueOf(Boolean.TRUE.equals(value)));
                } else if (evaluatedExpr.contains(fullMatch + " == false")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " == false", 
                        String.valueOf(Boolean.FALSE.equals(value)));
                } else if (evaluatedExpr.contains(fullMatch + " != null")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " != null", 
                        String.valueOf(value != null));
                } else if (evaluatedExpr.contains(fullMatch + " == null")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " == null", 
                        String.valueOf(value == null));
                }
            }

            return "true".equals(evaluatedExpr) || evaluatedExpr.isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 解析属性
     */
    private Object resolveProperty(Object obj, String propertyPath) {
        if (obj == null || propertyPath == null || propertyPath.isEmpty()) {
            return obj;
        }

        String[] properties = propertyPath.split("\\.");
        Object current = obj;

        for (String property : properties) {
            if (current == null) {
                return null;
            }
            try {
                String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                var method = current.getClass().getMethod(getterName);
                current = method.invoke(current);
            } catch (Exception e) {
                return null;
            }
        }

        return current;
    }
}
