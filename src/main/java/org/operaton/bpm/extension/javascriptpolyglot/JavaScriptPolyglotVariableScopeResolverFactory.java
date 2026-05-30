package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.scripting.engine.Resolver;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;

/**
 * Creates variable-scope resolvers with the plugin's JSON-aware value mapper.
 */
public class JavaScriptPolyglotVariableScopeResolverFactory implements ResolverFactory {

    private final JavaScriptPolyglotValueMapper valueMapper;

    public JavaScriptPolyglotVariableScopeResolverFactory(JavaScriptPolyglotValueMapper valueMapper) {
        this.valueMapper = valueMapper;
    }

    @Override
    public Resolver createResolver(VariableScope variableScope) {
        if (variableScope == null) {
            return null;
        }

        return new JavaScriptPolyglotVariableScopeResolver(variableScope, valueMapper);
    }
}
