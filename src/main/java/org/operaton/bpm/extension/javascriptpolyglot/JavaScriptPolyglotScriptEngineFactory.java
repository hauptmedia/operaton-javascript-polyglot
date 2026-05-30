package org.operaton.bpm.extension.javascriptpolyglot;

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;
import org.operaton.bpm.engine.impl.context.Context;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.List;

/**
 * JSR-223 factory registered through {@code META-INF/services}.
 *
 * <p>It creates GraalJS-backed engines under the JavaScript polyglot language
 * name and mirrors Operaton's GraalJS context options.</p>
 */
public class JavaScriptPolyglotScriptEngineFactory implements ScriptEngineFactory {

    public static final String LANGUAGE_NAME = PolyglotScriptLanguages.JAVASCRIPT;

    private static final String ENGINE_NAME = "Operaton JavaScript Polyglot";
    private static final String ALLOW_HOST_ACCESS = "polyglot.js.allowHostAccess";
    private static final String ALLOW_HOST_CLASS_LOOKUP = "polyglot.js.allowHostClassLookup";
    private static final String ALLOW_IO = "polyglot.js.allowIO";
    private static final String NASHORN_COMPATIBILITY = "polyglot.js.nashorn-compat";
    private static final List<String> NAMES = List.of(LANGUAGE_NAME, "operaton-javascript-polyglot");

    private final GraalJSEngineFactory delegate = new GraalJSEngineFactory();

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
        return List.of();
    }

    @Override
    public List<String> getMimeTypes() {
        return List.of();
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
        return delegate.getLanguageVersion();
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
        return new JavaScriptPolyglotScriptEngine(createConfiguredGraalJsScriptEngine(), this);
    }

    static ScriptEngine createConfiguredGraalJsScriptEngine() {
        var scriptEngine = new GraalJSEngineFactory().getScriptEngine();
        configureGraalJsScriptEngine(scriptEngine);

        return scriptEngine;
    }

    private static void configureGraalJsScriptEngine(ScriptEngine scriptEngine) {
        var processEngineConfiguration = Context.getProcessEngineConfiguration();

        if (processEngineConfiguration == null) {
            return;
        }

        if (processEngineConfiguration.isConfigureScriptEngineHostAccess()) {
            scriptEngine.getContext().setAttribute(ALLOW_HOST_ACCESS, true, ScriptContext.ENGINE_SCOPE);
            scriptEngine.getContext().setAttribute(ALLOW_HOST_CLASS_LOOKUP, true, ScriptContext.ENGINE_SCOPE);
        }

        if (processEngineConfiguration.isEnableScriptEngineLoadExternalResources()) {
            scriptEngine.getContext().setAttribute(ALLOW_IO, true, ScriptContext.ENGINE_SCOPE);
        }

        if (processEngineConfiguration.isEnableScriptEngineNashornCompatibility()) {
            scriptEngine.getContext().setAttribute(NASHORN_COMPATIBILITY, true, ScriptContext.ENGINE_SCOPE);
        }
    }
}
