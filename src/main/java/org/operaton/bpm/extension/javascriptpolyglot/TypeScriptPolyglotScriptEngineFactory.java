package org.operaton.bpm.extension.javascriptpolyglot;

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.List;

/**
 * JSR-223 factory for the opt-in {@code typescript-polyglot} language.
 *
 * <p>The exposed language is TypeScript, while execution is delegated to
 * GraalJS after transpilation by {@link TypeScriptPolyglotTranspiler}.</p>
 */
public class TypeScriptPolyglotScriptEngineFactory implements ScriptEngineFactory {

    public static final String LANGUAGE_NAME = PolyglotScriptLanguages.TYPESCRIPT;

    private static final String ENGINE_NAME = "Operaton TypeScript Polyglot";
    private static final List<String> EXTENSIONS = List.of("ts");
    private static final List<String> MIME_TYPES = List.of("text/typescript", "application/typescript");
    private static final List<String> NAMES = List.of(LANGUAGE_NAME, "operaton-typescript-polyglot");

    private final GraalJSEngineFactory delegate = new GraalJSEngineFactory();
    private final TypeScriptPolyglotTranspiler transpiler = new TypeScriptPolyglotTranspiler();

    @Override
    public String getEngineName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getEngineVersion() {
        return delegate.getEngineVersion();
    }

    @Override
    public List<String> getExtensions() {
        return EXTENSIONS;
    }

    @Override
    public List<String> getMimeTypes() {
        return MIME_TYPES;
    }

    @Override
    public List<String> getNames() {
        return NAMES;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getLanguageVersion() {
        return TypeScriptPolyglotTranspiler.TYPESCRIPT_VERSION;
    }

    @Override
    public Object getParameter(String key) {
        return switch (key) {
            case ScriptEngine.NAME -> LANGUAGE_NAME;
            case ScriptEngine.ENGINE -> ENGINE_NAME;
            case ScriptEngine.ENGINE_VERSION -> getEngineVersion();
            case ScriptEngine.LANGUAGE -> LANGUAGE_NAME;
            case ScriptEngine.LANGUAGE_VERSION -> getLanguageVersion();
            default -> delegate.getParameter(key);
        };
    }

    @Override
    public String getMethodCallSyntax(String objectName, String methodName, String... arguments) {
        return delegate.getMethodCallSyntax(objectName, methodName, arguments);
    }

    @Override
    public String getOutputStatement(String value) {
        return delegate.getOutputStatement(value);
    }

    @Override
    public String getProgram(String... statements) {
        return delegate.getProgram(statements);
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new TypeScriptPolyglotScriptEngine(
                JavaScriptPolyglotScriptEngineFactory.createConfiguredGraalJsScriptEngine(),
                this,
                transpiler
        );
    }
}
