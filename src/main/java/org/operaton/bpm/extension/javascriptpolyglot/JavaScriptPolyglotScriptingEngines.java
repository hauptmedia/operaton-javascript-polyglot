package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;
import org.operaton.bpm.engine.impl.scripting.engine.ScriptBindingsFactory;
import org.operaton.bpm.engine.impl.scripting.engine.ScriptEngineResolver;
import org.operaton.bpm.engine.impl.scripting.engine.ScriptingEngines;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.util.List;

/**
 * Scripting engine facade that selects a separate bindings factory for the
 * opt-in polyglot languages.
 *
 * <p>All other languages keep Operaton's original binding behavior. Polyglot
 * bindings are eagerly copied because GraalJS does not reliably resolve lazy
 * Operaton {@code ScriptBindings} for proxy-backed values.</p>
 */
public class JavaScriptPolyglotScriptingEngines extends ScriptingEngines {

    private final ScriptBindingsFactory defaultScriptBindingsFactory;
    private final ScriptBindingsFactory polyglotBindingsFactory;

    public JavaScriptPolyglotScriptingEngines(List<ResolverFactory> defaultResolverFactories, List<ResolverFactory> polyglotResolverFactories, ScriptEngineResolver scriptEngineResolver) {
        super(new ScriptBindingsFactory(defaultResolverFactories), scriptEngineResolver);

        defaultScriptBindingsFactory = getScriptBindingsFactory();
        polyglotBindingsFactory = new ScriptBindingsFactory(polyglotResolverFactories);
    }

    @Override
    public Bindings createBindings(ScriptEngine scriptEngine, VariableScope variableScope) {
        if (isPolyglotEngine(scriptEngine)) {
            return createEagerBindings(scriptEngine, variableScope, polyglotBindingsFactory);
        }

        if (!isCachableEngine(scriptEngine)) {
            return createBindingsForNonCachableEngine(scriptEngine, variableScope, defaultScriptBindingsFactory);
        }

        return defaultScriptBindingsFactory.createBindings(variableScope, scriptEngine.createBindings());
    }

    private Bindings createEagerBindings(ScriptEngine scriptEngine, VariableScope variableScope, ScriptBindingsFactory bindingsFactory) {
        var bindings = isCachableEngine(scriptEngine)
                ? scriptEngine.createBindings()
                : scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);

        copyResolverBindings(variableScope, bindingsFactory, bindings);

        return bindings;
    }

    private Bindings createBindingsForNonCachableEngine(ScriptEngine scriptEngine, VariableScope variableScope, ScriptBindingsFactory bindingsFactory) {
        var bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);

        copyResolverBindings(variableScope, bindingsFactory, bindings);

        return bindings;
    }

    private void copyResolverBindings(VariableScope variableScope, ScriptBindingsFactory bindingsFactory, Bindings bindings) {
        for (var resolverFactory : bindingsFactory.getResolverFactories()) {
            var resolver = resolverFactory.createResolver(variableScope);

            if (resolver == null) {
                continue;
            }

            for (var key : resolver.keySet()) {
                bindings.put(key, resolver.get(key));
            }
        }
    }

    private boolean isPolyglotEngine(ScriptEngine scriptEngine) {
        var factory = scriptEngine.getFactory();

        if (PolyglotScriptLanguages.isPolyglotLanguage(factory.getEngineName())
                || PolyglotScriptLanguages.isPolyglotLanguage(factory.getLanguageName())) {
            return true;
        }

        return factory.getNames().stream().anyMatch(PolyglotScriptLanguages::isPolyglotLanguage);
    }
}
