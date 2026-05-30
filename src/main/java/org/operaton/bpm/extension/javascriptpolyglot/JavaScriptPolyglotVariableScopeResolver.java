package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.scripting.engine.Resolver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exposes process variables and the execution/task scope object to polyglot
 * scripts.
 */
public class JavaScriptPolyglotVariableScopeResolver implements Resolver {

    private final VariableScope variableScope;
    private final String variableScopeKey;
    private final JavaScriptPolyglotValueMapper valueMapper;

    public JavaScriptPolyglotVariableScopeResolver(VariableScope variableScope, JavaScriptPolyglotValueMapper valueMapper) {
        this.variableScope = variableScope;
        this.variableScopeKey = variableScope.getVariableScopeKey();
        this.valueMapper = valueMapper;
    }

    @Override
    public boolean containsKey(Object key) {
        return variableScopeKey.equals(key) || key instanceof String variableName && variableScope.hasVariable(variableName);
    }

    @Override
    public Object get(Object key) {
        if (variableScopeKey.equals(key)) {
            return valueMapper.toJavaScriptValue(variableScope);
        }

        var variableName = (String) key;

        return valueMapper.toJavaScriptProcessVariable(variableName, variableScope.getVariable(variableName), variableScope);
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new LinkedHashSet<>(variableScope.getVariableNames());
        keys.add(variableScopeKey);

        return keys;
    }
}
