package org.operaton.bpm.extension.javascriptpolyglot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable reflection snapshot used by {@link JavaScriptPolyglotHostObject}.
 *
 * <p>Instances are cached per Java class so script access does not repeatedly scan
 * the same methods and fields.</p>
 */
public class JavaScriptPolyglotHostObjectMetadata {

    private static final Set<String> IGNORED_OBJECT_METHODS = Set.of("getClass", "wait", "notify", "notifyAll");

    private final Map<String, List<Method>> methods;
    private final Map<String, Method> getters;
    private final Map<String, List<Method>> setters;
    private final Map<String, Field> fields;

    private JavaScriptPolyglotHostObjectMetadata(
            Map<String, List<Method>> methods,
            Map<String, Method> getters,
            Map<String, List<Method>> setters,
            Map<String, Field> fields
    ) {
        this.methods = methods;
        this.getters = getters;
        this.setters = setters;
        this.fields = fields;
    }

    static JavaScriptPolyglotHostObjectMetadata fromClass(Class<?> sourceClass) {
        var methods = collectMethods(sourceClass);

        return new JavaScriptPolyglotHostObjectMetadata(
                methods,
                collectGetters(methods),
                collectSetters(methods),
                collectFields(sourceClass)
        );
    }

    Map<String, List<Method>> methods() {
        return methods;
    }

    Map<String, Method> getters() {
        return getters;
    }

    Map<String, List<Method>> setters() {
        return setters;
    }

    Map<String, Field> fields() {
        return fields;
    }

    private static Map<String, List<Method>> collectMethods(Class<?> sourceClass) {
        Map<String, List<Method>> methods = Arrays.stream(sourceClass.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !IGNORED_OBJECT_METHODS.contains(method.getName()))
                .sorted(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString))
                .collect(Collectors.groupingBy(Method::getName, LinkedHashMap::new, Collectors.toList()));

        methods.replaceAll((key, value) -> List.copyOf(value));

        return Collections.unmodifiableMap(methods);
    }

    private static Map<String, Method> collectGetters(Map<String, List<Method>> methods) {
        Map<String, Method> collectedGetters = new LinkedHashMap<>();

        methods.values().stream()
                .flatMap(List::stream)
                .filter(method -> method.getParameterCount() == 0)
                .forEach(method -> getterPropertyName(method).ifPresent(propertyName -> collectedGetters.putIfAbsent(propertyName, method)));

        return Collections.unmodifiableMap(collectedGetters);
    }

    private static Map<String, List<Method>> collectSetters(Map<String, List<Method>> methods) {
        Map<String, List<Method>> collectedSetters = new LinkedHashMap<>();

        methods.values().stream()
                .flatMap(List::stream)
                .filter(method -> method.getParameterCount() == 1)
                .forEach(method -> setterPropertyName(method).ifPresent(propertyName -> collectedSetters
                        .computeIfAbsent(propertyName, ignored -> new ArrayList<>())
                        .add(method)));

        collectedSetters.replaceAll((key, value) -> List.copyOf(value));

        return Collections.unmodifiableMap(collectedSetters);
    }

    private static Map<String, Field> collectFields(Class<?> sourceClass) {
        return Arrays.stream(sourceClass.getFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .sorted(Comparator.comparing(Field::getName))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Field::getName, field -> field, (first, second) -> first, LinkedHashMap::new),
                        Collections::unmodifiableMap
                ));
    }

    private static Optional<String> getterPropertyName(Method method) {
        var methodName = method.getName();

        if (methodName.startsWith("get") && methodName.length() > 3 && !methodName.equals("getClass")) {
            return Optional.of(decapitalize(methodName.substring(3)));
        }

        if (methodName.startsWith("is") && methodName.length() > 2) {
            var returnType = box(method.getReturnType());

            if (returnType.equals(Boolean.class)) {
                return Optional.of(decapitalize(methodName.substring(2)));
            }
        }

        return Optional.empty();
    }

    private static Optional<String> setterPropertyName(Method method) {
        var methodName = method.getName();

        if (methodName.startsWith("set") && methodName.length() > 3) {
            return Optional.of(decapitalize(methodName.substring(3)));
        }

        return Optional.empty();
    }

    private static String decapitalize(String value) {
        if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) {
            return value;
        }

        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static Class<?> box(Class<?> type) {
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
