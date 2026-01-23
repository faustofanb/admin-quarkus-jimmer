package io.github.faustofan.admin.shared.distributed.spel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分布式组件Key表达式解析器
 * <p>
 * 支持简化版 SpEL 语法，用于解析锁Key、幂等Key等表达式
 * <p>
 * 支持的表达式：
 * <ul>
 *   <li>{@code #paramName} - 方法参数名</li>
 *   <li>{@code #p0, #p1} - 方法参数索引</li>
 *   <li>{@code #param.property} - 参数属性（支持嵌套）</li>
 *   <li>字符串拼接：{@code 'prefix:' + #param}</li>
 *   <li>条件表达式：{@code #param != null}, {@code #param > 0}</li>
 * </ul>
 */
@ApplicationScoped
public class DistributedKeyExpressionParser {

    private static final Logger LOG = Logger.getLogger(DistributedKeyExpressionParser.class);

    // 匹配 #变量名 或 #变量名.属性 的模式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#(\\w+)(\\.\\w+)*");
    
    // 匹配 #p0, #p1 等参数索引的模式
    private static final Pattern INDEX_PATTERN = Pattern.compile("#p(\\d+)");

    /**
     * 解析Key表达式
     *
     * @param expression SpEL 表达式
     * @param context    调用上下文
     * @return 解析后的Key
     */
    public String parseKey(String expression, InvocationContext context) {
        if (expression == null || expression.isEmpty()) {
            return generateDefaultKey(context);
        }

        Map<String, Object> variables = buildVariableMap(context);
        return evaluateExpression(expression, variables);
    }

    /**
     * 解析条件表达式
     *
     * @param expression SpEL 条件表达式
     * @param context    调用上下文
     * @return 条件结果
     */
    public boolean parseCondition(String expression, InvocationContext context) {
        if (expression == null || expression.isEmpty()) {
            return true; // 空条件默认为 true
        }

        Map<String, Object> variables = buildVariableMap(context);
        return evaluateCondition(expression, variables);
    }

    /**
     * 生成默认Key
     */
    public String generateDefaultKey(InvocationContext context) {
        Method method = context.getMethod();
        String className = context.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        Object[] params = context.getParameters();

        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(className)
                  .append(":")
                  .append(methodName);

        if (params != null && params.length > 0) {
            keyBuilder.append(":");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    keyBuilder.append(",");
                }
                keyBuilder.append(hashParam(params[i]));
            }
        }

        return keyBuilder.toString();
    }

    /**
     * 构建变量映射
     */
    private Map<String, Object> buildVariableMap(InvocationContext context) {
        Map<String, Object> variables = new HashMap<>();

        Method method = context.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = context.getParameters();

        if (parameters != null && args != null) {
            for (int i = 0; i < parameters.length; i++) {
                if (i < args.length) {
                    String paramName = parameters[i].getName();
                    variables.put(paramName, args[i]);
                    variables.put("p" + i, args[i]);
                }
            }
        }

        return variables;
    }

    /**
     * 表达式求值
     */
    private String evaluateExpression(String expression, Map<String, Object> variables) {
        String result = expression;

        // 处理字符串拼接，移除单引号
        result = result.replace("'", "");

        // 处理加号拼接
        if (result.contains("+")) {
            String[] parts = result.split("\\+");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                sb.append(evaluatePart(part.trim(), variables));
            }
            return sb.toString();
        }

        return evaluatePart(result, variables);
    }

    /**
     * 求值表达式的一部分
     */
    private String evaluatePart(String part, Map<String, Object> variables) {
        // 匹配 #参数索引 模式
        Matcher indexMatcher = INDEX_PATTERN.matcher(part);
        if (indexMatcher.find()) {
            int index = Integer.parseInt(indexMatcher.group(1));
            Object value = variables.get("p" + index);
            return substituteVariable(part, indexMatcher.group(), value);
        }

        // 匹配 #变量名.属性 模式
        Matcher varMatcher = VARIABLE_PATTERN.matcher(part);
        String result = part;
        while (varMatcher.find()) {
            String fullMatch = varMatcher.group();
            String varName = varMatcher.group(1);
            String propertyPath = varMatcher.group(2);

            Object value = variables.get(varName);
            if (value != null && propertyPath != null && !propertyPath.isEmpty()) {
                value = resolveProperty(value, propertyPath.substring(1));
            }
            result = result.replace(fullMatch, stringValue(value));
        }

        return result;
    }

    /**
     * 替换变量
     */
    private String substituteVariable(String expression, String variable, Object value) {
        return expression.replace(variable, stringValue(value));
    }

    /**
     * 解析对象属性
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
            current = getPropertyValue(current, property);
        }

        return current;
    }

    /**
     * 获取对象属性值（通过反射）
     */
    private Object getPropertyValue(Object obj, String property) {
        try {
            String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            var method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                String isName = "is" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                var method = obj.getClass().getMethod(isName);
                return method.invoke(obj);
            } catch (Exception e2) {
                try {
                    var field = obj.getClass().getDeclaredField(property);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (Exception e3) {
                    LOG.warnv("Failed to get property '{0}' from {1}", property, obj.getClass().getName());
                    return null;
                }
            }
        }
    }

    /**
     * 条件表达式求值
     */
    private boolean evaluateCondition(String expression, Map<String, Object> variables) {
        try {
            String evaluatedExpr = expression;

            Matcher varMatcher = VARIABLE_PATTERN.matcher(expression);
            while (varMatcher.find()) {
                String fullMatch = varMatcher.group();
                String varName = varMatcher.group(1);
                String propertyPath = varMatcher.group(2);

                Object value = variables.get(varName);
                if (value != null && propertyPath != null && !propertyPath.isEmpty()) {
                    value = resolveProperty(value, propertyPath.substring(1));
                }

                if (evaluatedExpr.contains(fullMatch + " != null")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " != null", String.valueOf(value != null));
                } else if (evaluatedExpr.contains(fullMatch + " == null")) {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch + " == null", String.valueOf(value == null));
                } else {
                    evaluatedExpr = evaluatedExpr.replace(fullMatch, stringValue(value));
                }
            }

            if (evaluatedExpr.equals("true")) {
                return true;
            }
            if (evaluatedExpr.equals("false")) {
                return false;
            }

            if (evaluatedExpr.contains(" > ")) {
                String[] parts = evaluatedExpr.split(" > ");
                return parseNumber(parts[0].trim()) > parseNumber(parts[1].trim());
            }
            if (evaluatedExpr.contains(" < ")) {
                String[] parts = evaluatedExpr.split(" < ");
                return parseNumber(parts[0].trim()) < parseNumber(parts[1].trim());
            }
            if (evaluatedExpr.contains(" >= ")) {
                String[] parts = evaluatedExpr.split(" >= ");
                return parseNumber(parts[0].trim()) >= parseNumber(parts[1].trim());
            }
            if (evaluatedExpr.contains(" <= ")) {
                String[] parts = evaluatedExpr.split(" <= ");
                return parseNumber(parts[0].trim()) <= parseNumber(parts[1].trim());
            }
            if (evaluatedExpr.contains(" == ")) {
                String[] parts = evaluatedExpr.split(" == ");
                return parts[0].trim().equals(parts[1].trim());
            }
            if (evaluatedExpr.contains(" != ")) {
                String[] parts = evaluatedExpr.split(" != ");
                return !parts[0].trim().equals(parts[1].trim());
            }

            return true;

        } catch (Exception e) {
            LOG.warnv("Failed to evaluate condition: {0}, error: {1}", expression, e.getMessage());
            return true;
        }
    }

    private double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value);
    }

    private String hashParam(Object param) {
        if (param == null) {
            return "null";
        }
        if (param instanceof String || param instanceof Number || param instanceof Boolean) {
            return String.valueOf(param);
        }
        return String.valueOf(param.hashCode());
    }
}
