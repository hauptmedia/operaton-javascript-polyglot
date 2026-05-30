package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.scripting.engine.Resolver;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;

/**
 * Creates {@link JavaScriptPolyglotMappingResolver} instances for existing
 * Operaton resolver factories.
 */
public class JavaScriptPolyglotMappingResolverFactory implements ResolverFactory {

    private final ResolverFactory delegate;
    private final JavaScriptPolyglotValueMapper valueMapper;

    public JavaScriptPolyglotMappingResolverFactory(ResolverFactory delegate, JavaScriptPolyglotValueMapper valueMapper) {
        this.delegate = delegate;
        this.valueMapper = valueMapper;
    }

    ResolverFactory getDelegate() {
        return delegate;
    }

    @Override
    public Resolver createResolver(VariableScope variableScope) {
        var resolver = delegate.createResolver(variableScope);

        if (resolver == null) {
            return null;
        }

        return new JavaScriptPolyglotMappingResolver(resolver, valueMapper);
    }
}
