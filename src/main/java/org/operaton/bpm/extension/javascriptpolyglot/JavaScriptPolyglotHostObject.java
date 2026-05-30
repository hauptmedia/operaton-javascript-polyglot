package org.operaton.bpm.extension.javascriptpolyglot;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GraalVM {@link ProxyObject} adapter for regular Java objects.
 *
 * <p>The adapter gives scripts JavaScript-style property access to public fields,
 * JavaBean getters/setters and public methods while still converting arguments and
 * results through the plugin value mapper.</p>
 */
public class JavaScriptPolyglotHostObject implements ProxyObject {

    /**
     * Keeps the selected method and its conversion score together so overload
     * resolution scores each candidate only once.
     */
    private record ScoredMethod(Method method, int score) {
    }

    private final Object source;
    private final JavaScriptPolyglotValueMapper valueMapper;
    private final JavaScriptPolyglotHostObjectMetadata metadata;

    public JavaScriptPolyglotHostObject(Object source, JavaScriptPolyglotValueMapper valueMapper, JavaScriptPolyglotHostObjectMetadata metadata) {
        this.source = source;
        this.valueMapper = valueMapper;
        this.metadata = metadata;
    }

    Object getSource() {
        return source;
    }

    @Override
    public Object getMember(String key) {
        var namedMethods = metadata.methods().get(key);

        if (namedMethods != null && !namedMethods.isEmpty()) {
            return (ProxyExecutable) arguments -> invokeBestMatchingMethod(namedMethods, arguments);
        }

        var getter = metadata.getters().get(key);

        if (getter != null) {
            return valueMapper.toJavaScriptValue(invoke(getter));
        }

        var field = metadata.fields().get(key);

        if (field != null) {
            return valueMapper.toJavaScriptValue(readField(field));
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(metadata.methods().keySet());
        keys.addAll(metadata.getters().keySet());
        keys.addAll(metadata.fields().keySet());

        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return metadata.methods().containsKey(key) || metadata.getters().containsKey(key) || metadata.fields().containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        var argument = valueMapper.toPlainJavaValue(value);
        var setter = selectBestSetter(metadata.setters().get(key), argument);

        if (setter != null) {
            invoke(setter, buildInvocationArguments(setter, Collections.singletonList(argument)));
            return;
        }

        var field = metadata.fields().get(key);

        if (field == null) {
            throw new UnsupportedOperationException("Cannot set member '%s' on %s".formatted(key, source.getClass().getName()));
        }

        writeField(field, argument);
    }

    private Object invokeBestMatchingMethod(List<Method> candidates, Value[] arguments) {
        var javaArguments = Arrays.stream(arguments)
                .map(valueMapper::toPlainJavaValue)
                .toList();
        var method = selectBestMethod(candidates, javaArguments);
        var invocationArguments = buildInvocationArguments(method, javaArguments);

        return valueMapper.toJavaScriptMethodResult(source, method, javaArguments, invoke(method, invocationArguments));
    }

    private Method selectBestMethod(List<Method> candidates, List<Object> arguments) {
        return candidates.stream()
                .filter(method -> acceptsArgumentCount(method, arguments.size()))
                .map(method -> new ScoredMethod(method, scoreMethod(method, arguments)))
                .filter(candidate -> candidate.score() < Integer.MAX_VALUE)
                .min(Comparator.comparingInt(ScoredMethod::score))
                .map(ScoredMethod::method)
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No matching method on %s for %d arguments".formatted(source.getClass().getName(), arguments.size())
                ));
    }

    private Method selectBestSetter(List<Method> candidates, Object argument) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        var arguments = Collections.singletonList(argument);

        return candidates.stream()
                .filter(method -> acceptsArgumentCount(method, 1))
                .map(method -> new ScoredMethod(method, scoreMethod(method, arguments)))
                .filter(candidate -> candidate.score() < Integer.MAX_VALUE)
                .min(Comparator.comparingInt(ScoredMethod::score))
                .map(ScoredMethod::method)
                .orElse(null);
    }

    private boolean acceptsArgumentCount(Method method, int argumentCount) {
        var parameterCount = method.getParameterCount();

        if (method.isVarArgs()) {
            return argumentCount >= parameterCount - 1;
        }

        return argumentCount == parameterCount;
    }

    private int scoreMethod(Method method, List<Object> arguments) {
        var score = method.isVarArgs() ? 2 : 0;
        var parameterTypes = method.getParameterTypes();
        var fixedParameterCount = method.isVarArgs() ? parameterTypes.length - 1 : parameterTypes.length;

        for (var index = 0; index < fixedParameterCount; index++) {
            var argumentScore = scoreArgument(parameterTypes[index], arguments.get(index));

            if (argumentScore == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }

            score += argumentScore;
        }

        if (method.isVarArgs()) {
            var componentType = parameterTypes[parameterTypes.length - 1].getComponentType();

            for (var index = fixedParameterCount; index < arguments.size(); index++) {
                var argumentScore = scoreArgument(componentType, arguments.get(index));

                if (argumentScore == Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }

                score += argumentScore;
            }
        }

        return score;
    }

    private int scoreArgument(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return parameterType.isPrimitive() ? Integer.MAX_VALUE : 1;
        }

        var boxedParameterType = box(parameterType);

        if (boxedParameterType.equals(Object.class)) {
            return 5;
        }

        if (boxedParameterType.equals(argument.getClass())) {
            return 0;
        }

        if (boxedParameterType.isInstance(argument)) {
            return 1;
        }

        if (Number.class.isAssignableFrom(boxedParameterType) && argument instanceof Number) {
            return 2;
        }

        if (boxedParameterType.equals(String.class) && argument instanceof CharSequence) {
            return 2;
        }

        if (valueMapper.canConvertToJavaArgument(argument, parameterType)) {
            return 3;
        }

        return Integer.MAX_VALUE;
    }

    private Object[] buildInvocationArguments(Method method, List<Object> arguments) {
        var parameterTypes = method.getParameterTypes();

        if (!method.isVarArgs()) {
            var invocationArguments = new Object[arguments.size()];

            for (var index = 0; index < arguments.size(); index++) {
                invocationArguments[index] = convertArgument(method, index, arguments.get(index), parameterTypes[index]);
            }

            return invocationArguments;
        }

        var fixedParameterCount = parameterTypes.length - 1;
        var invocationArguments = new Object[parameterTypes.length];

        for (var index = 0; index < fixedParameterCount; index++) {
            invocationArguments[index] = convertArgument(method, index, arguments.get(index), parameterTypes[index]);
        }

        var componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
        var varargs = java.lang.reflect.Array.newInstance(componentType, arguments.size() - fixedParameterCount);

        for (var index = fixedParameterCount; index < arguments.size(); index++) {
            java.lang.reflect.Array.set(varargs, index - fixedParameterCount, convertArgument(method, index, arguments.get(index), componentType));
        }

        invocationArguments[parameterTypes.length - 1] = varargs;

        return invocationArguments;
    }

    private Object convertArgument(Method method, int argumentIndex, Object argument, Class<?> parameterType) {
        return convertArgument(valueMapper.toJavaMethodArgument(source, method, argumentIndex, argument, parameterType), parameterType);
    }

    private Object convertArgument(Object argument, Class<?> parameterType) {
        if (argument == null) {
            return null;
        }

        var boxedParameterType = box(parameterType);

        if (boxedParameterType.isInstance(argument) || boxedParameterType.equals(Object.class)) {
            return argument;
        }

        if (argument instanceof Number number) {
            if (boxedParameterType.equals(Integer.class)) {
                return number.intValue();
            }

            if (boxedParameterType.equals(Long.class)) {
                return number.longValue();
            }

            if (boxedParameterType.equals(Double.class)) {
                return number.doubleValue();
            }

            if (boxedParameterType.equals(Float.class)) {
                return number.floatValue();
            }

            if (boxedParameterType.equals(Short.class)) {
                return number.shortValue();
            }

            if (boxedParameterType.equals(Byte.class)) {
                return number.byteValue();
            }
        }

        if (boxedParameterType.equals(String.class) && argument instanceof CharSequence) {
            return argument.toString();
        }

        return argument;
    }

    private Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(source, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access method %s on %s".formatted(method.getName(), source.getClass().getName()), exception);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new IllegalStateException("Method %s on %s failed".formatted(method.getName(), source.getClass().getName()), cause);
        }
    }

    private Object readField(Field field) {
        try {
            return field.get(source);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access field %s on %s".formatted(field.getName(), source.getClass().getName()), exception);
        }
    }

    private void writeField(Field field, Object value) {
        try {
            field.set(source, convertArgument(valueMapper.toJavaArgument(value, field.getType()), field.getType()));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access field %s on %s".formatted(field.getName(), source.getClass().getName()), exception);
        }
    }

    private Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }

        if (type.equals(boolean.class)) {
            return Boolean.class;
        }

        if (type.equals(int.class)) {
            return Integer.class;
        }

        if (type.equals(long.class)) {
            return Long.class;
        }

        if (type.equals(double.class)) {
            return Double.class;
        }

        if (type.equals(float.class)) {
            return Float.class;
        }

        if (type.equals(short.class)) {
            return Short.class;
        }

        if (type.equals(byte.class)) {
            return Byte.class;
        }

        if (type.equals(char.class)) {
            return Character.class;
        }

        return Void.class;
    }
}
