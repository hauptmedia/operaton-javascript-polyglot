package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.context.Context;
import org.operaton.bpm.engine.impl.scripting.engine.Resolver;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exposes configured Operaton engine beans to polyglot scripts.
 *
 * <p>Bean values are passed through {@link JavaScriptPolyglotValueMapper} so JSON
 * results and Java host objects behave consistently with process variables.</p>
 */
public class JavaScriptPolyglotBeansResolverFactory implements ResolverFactory, Resolver {

    private final JavaScriptPolyglotValueMapper valueMapper;

    public JavaScriptPolyglotBeansResolverFactory(JavaScriptPolyglotValueMapper valueMapper) {
        this.valueMapper = valueMapper;
    }

    @Override
    public Resolver createResolver(VariableScope variableScope) {
        return this;
    }

    @Override
    public boolean containsKey(Object key) {
        return getBeans().containsKey(key);
    }

    @Override
    public Object get(Object key) {
        return valueMapper.toJavaScriptValue(getBeans().get(key));
    }

    @Override
    public Set<String> keySet() {
        return getBeans().keySet().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Object, Object> getBeans() {
        var processEngineConfiguration = Context.getProcessEngineConfiguration();

        if (processEngineConfiguration == null || processEngineConfiguration.getBeans() == null) {
            return Collections.emptyMap();
        }

        return processEngineConfiguration.getBeans();
    }
}
