package org.operaton.bpm.extension.javascriptpolyglot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.spin.Spin;
import org.operaton.spin.json.SpinJsonNode;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central conversion layer between Operaton Java values, Spin JSON values and
 * GraalVM JavaScript values.
 *
 * <p>It creates mutable JSON proxies for scripts, converts script-written JSON
 * back into Spin values for process variables and wraps Java host objects for
 * JavaScript-style property access.</p>
 */
public class JavaScriptPolyglotValueMapper {

    private static final Runnable NOOP_CHANGE_LISTENER = () -> {
    };

    private static final ClassValue<JavaScriptPolyglotHostObjectMetadata> HOST_OBJECT_METADATA = new ClassValue<>() {

        @Override
        protected JavaScriptPolyglotHostObjectMetadata computeValue(Class<?> type) {
            return JavaScriptPolyglotHostObjectMetadata.fromClass(type);
        }
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Object toJavaScriptValue(Object value) {
        if (value == null || isJavaScriptPrimitive(value) || value instanceof Proxy) {
            return value;
        }

        if (value instanceof SpinJsonNode spinJsonNode) {
            return toJavaScriptValue(readJson(spinJsonNode.toString()));
        }

        if (value instanceof Value polyglotValue) {
            return toJavaScriptValue(toPlainJavaValue(polyglotValue));
        }

        if (isJsonContainer(value)) {
            return toJavaScriptJsonValue(toPlainJavaValue(value), NOOP_CHANGE_LISTENER);
        }

        return toJavaScriptHostObject(value);
    }

    public Object toJavaScriptProcessVariable(String variableName, Object value, VariableScope variableScope) {
        return toJavaScriptProcessVariable(variableName, value, variableScope, false);
    }

    public Object toJavaScriptMethodResult(Object source, Method method, List<Object> arguments, Object result) {
        if (source instanceof VariableScope variableScope && !arguments.isEmpty() && arguments.getFirst() instanceof String variableName) {
            if ("getVariable".equals(method.getName())) {
                return toJavaScriptProcessVariable(variableName, result, variableScope, false);
            }

            if ("getVariableLocal".equals(method.getName())) {
                return toJavaScriptProcessVariable(variableName, result, variableScope, true);
            }
        }

        return toJavaScriptValue(result);
    }

    Object toJavaArgument(Object value, Class<?> targetType) {
        var plainValue = toPlainJavaValue(value);

        if (canConvertToJavaArgument(plainValue, targetType)) {
            return Spin.JSON(writeJson(plainValue));
        }

        return plainValue;
    }

    boolean canConvertToJavaArgument(Object value, Class<?> targetType) {
        return isJsonContainer(value) && acceptsSpinJsonNode(targetType);
    }

    Object toJavaMethodArgument(Object source, Method method, int argumentIndex, Object value, Class<?> targetType) {
        if (source instanceof VariableScope && isVariableScopeValueSetter(method, argumentIndex)) {
            return toProcessVariableValue(value);
        }

        if (source instanceof VariableScope && isVariableScopeVariablesSetter(method, argumentIndex)) {
            return toProcessVariableMap(value);
        }

        return toJavaArgument(value, targetType);
    }

    Object toPlainJavaValue(Object value) {
        if (value == null || isJavaScriptPrimitive(value)) {
            return value;
        }

        if (value instanceof Value polyglotValue) {
            return toPlainJavaValue(polyglotValue);
        }

        if (value instanceof SpinJsonNode spinJsonNode) {
            return readJson(spinJsonNode.toString());
        }

        if (value instanceof JavaScriptPolyglotHostObject hostObject) {
            return hostObject.getSource();
        }

        if (value instanceof JavaScriptPolyglotJsonObject jsonObject) {
            return jsonObject.toPlainJavaMap();
        }

        if (value instanceof JavaScriptPolyglotJsonArray jsonArray) {
            return jsonArray.toPlainJavaList();
        }

        if (value instanceof ProxyObject proxyObject) {
            return toPlainJavaMap(proxyObject);
        }

        if (value instanceof ProxyArray proxyArray) {
            return toPlainJavaList(proxyArray);
        }

        if (value instanceof Map<?, ?> map) {
            return toPlainJavaMap(map);
        }

        if (value instanceof Iterable<?> iterable) {
            return toPlainJavaList(iterable);
        }

        if (isJsonArray(value)) {
            return toPlainJavaList(value);
        }

        return value;
    }

    private Object toPlainJavaValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isHostObject()) {
            return toPlainJavaValue(value.asHostObject());
        }

        if (value.isProxyObject()) {
            var proxyObject = value.<Proxy>asProxyObject();

            if (proxyObject instanceof JavaScriptPolyglotHostObject hostObject) {
                return hostObject.getSource();
            }

            if (proxyObject instanceof JavaScriptPolyglotJsonObject jsonObject) {
                return jsonObject.toPlainJavaMap();
            }

            if (proxyObject instanceof JavaScriptPolyglotJsonArray jsonArray) {
                return jsonArray.toPlainJavaList();
            }

            if (proxyObject instanceof ProxyArray proxyArray) {
                return toPlainJavaList(proxyArray);
            }

            if (proxyObject instanceof ProxyObject object) {
                return toPlainJavaMap(object);
            }
        }

        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();

            for (var index = 0L; index < value.getArraySize(); index++) {
                list.add(toPlainJavaValue(value.getArrayElement(index)));
            }

            return list;
        }

        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();

            for (var key : value.getMemberKeys()) {
                var member = value.getMember(key);

                if (member != null && !member.canExecute()) {
                    map.put(key, toPlainJavaValue(member));
                }
            }

            return map;
        }

        if (value.isString()) {
            return value.asString();
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isNumber()) {
            return toJavaNumber(value);
        }

        return value.as(Object.class);
    }

    Object toJavaScriptJsonValue(Object value, Runnable changeListener) {
        if (value == null || isJavaScriptPrimitive(value) || value instanceof Proxy) {
            return value;
        }

        if (value instanceof SpinJsonNode spinJsonNode) {
            return toJavaScriptJsonValue(readJson(spinJsonNode.toString()), changeListener);
        }

        if (value instanceof Value polyglotValue) {
            return toJavaScriptJsonValue(toPlainJavaValue(polyglotValue), changeListener);
        }

        if (value instanceof Map<?, ?> map) {
            return new JavaScriptPolyglotJsonObject(asJsonMap(map), this, changeListener);
        }

        if (value instanceof Iterable<?> iterable) {
            return new JavaScriptPolyglotJsonArray(asJsonList(iterable), this, changeListener);
        }

        if (isJsonArray(value)) {
            return new JavaScriptPolyglotJsonArray(toPlainJavaList(value), this, changeListener);
        }

        return value;
    }

    private Number toJavaNumber(Value value) {
        if (value.fitsInInt()) {
            return value.asInt();
        }

        if (value.fitsInLong()) {
            return value.asLong();
        }

        if (value.fitsInDouble()) {
            return value.asDouble();
        }

        return value.as(Number.class);
    }

    private Map<String, Object> toPlainJavaMap(Map<?, ?> source) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (var entry : source.entrySet()) {
            map.put(String.valueOf(entry.getKey()), toPlainJavaValue(entry.getValue()));
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asJsonMap(Map<?, ?> source) {
        if (source.keySet().stream().allMatch(String.class::isInstance)) {
            return (Map<String, Object>) source;
        }

        return toPlainJavaMap(source);
    }

    private Map<String, Object> toPlainJavaMap(ProxyObject source) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (var key : getMemberKeys(source)) {
            map.put(key, toPlainJavaValue(source.getMember(key)));
        }

        return map;
    }

    private List<Object> toPlainJavaList(Iterable<?> source) {
        List<Object> list = new ArrayList<>();

        for (var value : source) {
            list.add(toPlainJavaValue(value));
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asJsonList(Iterable<?> source) {
        if (source instanceof List<?> list) {
            return (List<Object>) list;
        }

        return toPlainJavaList(source);
    }

    private List<Object> toPlainJavaList(Object sourceArray) {
        List<Object> list = new ArrayList<>();
        var length = Array.getLength(sourceArray);

        for (var index = 0; index < length; index++) {
            list.add(toPlainJavaValue(Array.get(sourceArray, index)));
        }

        return list;
    }

    private List<Object> toPlainJavaList(ProxyArray source) {
        List<Object> list = new ArrayList<>();

        for (var index = 0L; index < source.getSize(); index++) {
            list.add(toPlainJavaValue(source.get(index)));
        }

        return list;
    }

    private List<String> getMemberKeys(ProxyObject source) {
        var memberKeys = source.getMemberKeys();

        if (memberKeys instanceof String[] keys) {
            return List.of(keys);
        }

        if (memberKeys instanceof Iterable<?> iterable) {
            List<String> keys = new ArrayList<>();

            for (var key : iterable) {
                keys.add(String.valueOf(key));
            }

            return keys;
        }

        if (memberKeys instanceof ProxyArray proxyArray) {
            List<String> keys = new ArrayList<>();

            for (var index = 0L; index < proxyArray.getSize(); index++) {
                keys.add(String.valueOf(proxyArray.get(index)));
            }

            return keys;
        }

        if (memberKeys instanceof Value value && value.hasArrayElements()) {
            List<String> keys = new ArrayList<>();

            for (var index = 0L; index < value.getArraySize(); index++) {
                keys.add(String.valueOf(toPlainJavaValue(value.getArrayElement(index))));
            }

            return keys;
        }

        return List.of();
    }

    private Object readJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot read Spin JSON value", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot write JavaScript value as JSON", exception);
        }
    }

    private Object toJavaScriptProcessVariable(String variableName, Object value, VariableScope variableScope, boolean local) {
        var plainValue = toPlainJavaValue(value);

        if (!isJsonContainer(plainValue)) {
            return toJavaScriptValue(plainValue);
        }

        return toJavaScriptJsonValue(plainValue, () -> {
            var spinValue = Spin.JSON(writeJson(plainValue));

            if (local) {
                variableScope.setVariableLocal(variableName, spinValue);
            } else {
                variableScope.setVariable(variableName, spinValue);
            }
        });
    }

    private Object toJavaScriptHostObject(Object value) {
        return new JavaScriptPolyglotHostObject(value, this, HOST_OBJECT_METADATA.get(value.getClass()));
    }

    private boolean isJavaScriptPrimitive(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof Date
                || value instanceof Temporal
                || value instanceof UUID;
    }

    private boolean isJsonContainer(Object value) {
        return value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || isJsonArray(value);
    }

    private boolean isJsonArray(Object value) {
        return value != null && value.getClass().isArray() && !value.getClass().getComponentType().equals(byte.class);
    }

    private Object toProcessVariableValue(Object value) {
        var plainValue = toPlainJavaValue(value);

        if (isJsonContainer(plainValue)) {
            return Spin.JSON(writeJson(plainValue));
        }

        return plainValue;
    }

    private Map<String, Object> toProcessVariableMap(Object value) {
        var plainValue = toPlainJavaValue(value);

        if (!(plainValue instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> processVariables = new LinkedHashMap<>();

        for (var entry : map.entrySet()) {
            processVariables.put(String.valueOf(entry.getKey()), toProcessVariableValue(entry.getValue()));
        }

        return processVariables;
    }

    private boolean acceptsSpinJsonNode(Class<?> targetType) {
        return !targetType.equals(Object.class) && targetType.isAssignableFrom(SpinJsonNode.class);
    }

    private boolean isVariableScopeValueSetter(Method method, int argumentIndex) {
        return argumentIndex == 1
                && ("setVariable".equals(method.getName()) || "setVariableLocal".equals(method.getName()))
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0].equals(String.class);
    }

    private boolean isVariableScopeVariablesSetter(Method method, int argumentIndex) {
        return argumentIndex == 0
                && ("setVariables".equals(method.getName()) || "setVariablesLocal".equals(method.getName()))
                && method.getParameterCount() == 1
                && Map.class.isAssignableFrom(method.getParameterTypes()[0]);
    }
}
