package org.operaton.bpm.extension.javascriptpolyglot;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

/**
 * GraalJS-backed script engine for {@code typescript-polyglot}.
 *
 * <p>Operaton still executes JavaScript through GraalJS. This wrapper transpiles
 * TypeScript source before delegating to the shared JavaScript polyglot engine
 * behavior, so the same JSON mapping and write-back semantics apply.</p>
 */
public class TypeScriptPolyglotScriptEngine extends JavaScriptPolyglotScriptEngine {

    private final TypeScriptPolyglotTranspiler transpiler;

    TypeScriptPolyglotScriptEngine(ScriptEngine delegate, ScriptEngineFactory factory, TypeScriptPolyglotTranspiler transpiler) {
        super(delegate, factory);
        this.transpiler = transpiler;
    }

    @Override
    protected String prepareScript(String script, String sourceName) throws ScriptException {
        return transpiler.transpile(script, sourceName);
    }
}
