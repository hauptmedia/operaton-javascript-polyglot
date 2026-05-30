package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.impl.scripting.engine.Resolver;

import java.util.Set;

/**
 * Decorates an existing Operaton script resolver with polyglot value conversion.
 */
public class JavaScriptPolyglotMappingResolver implements Resolver {

    private final Resolver delegate;
    private final JavaScriptPolyglotValueMapper valueMapper;

    public JavaScriptPolyglotMappingResolver(Resolver delegate, JavaScriptPolyglotValueMapper valueMapper) {
        this.delegate = delegate;
        this.valueMapper = valueMapper;
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public Object get(Object key) {
        return valueMapper.toJavaScriptValue(delegate.get(key));
    }

    @Override
    public Set<String> keySet() {
        return delegate.keySet();
    }
}
