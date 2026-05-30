package org.operaton.bpm.extension.javascriptpolyglot;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

/**
 * Thin JSR-223 wrapper around GraalJS that reports an opt-in script language
 * identity.
 *
 * <p>The delegate still performs all JavaScript evaluation, compilation and
 * invocation work. The wrapper gives Operaton a distinct factory identity so
 * custom bindings can be scoped to the polyglot languages. Subclasses may
 * transform source before it reaches GraalJS.</p>
 */
public class JavaScriptPolyglotScriptEngine implements ScriptEngine, Compilable, Invocable {

    private final ScriptEngine delegate;
    private final ScriptEngineFactory factory;

    JavaScriptPolyglotScriptEngine(ScriptEngine delegate, ScriptEngineFactory factory) {
        this.delegate = delegate;
        this.factory = factory;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        return delegate.eval(prepareScript(script, "<eval>"), context);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return delegate.eval(prepareScript(readScript(reader), "<eval>"), context);
    }

    @Override
    public Object eval(String script) throws ScriptException {
        return delegate.eval(prepareScript(script, "<eval>"));
    }

    @Override
    public Object eval(Reader reader) throws ScriptException {
        return delegate.eval(prepareScript(readScript(reader), "<eval>"));
    }

    @Override
    public Object eval(String script, Bindings bindings) throws ScriptException {
        return delegate.eval(prepareScript(script, "<eval>"), bindings);
    }

    @Override
    public Object eval(Reader reader, Bindings bindings) throws ScriptException {
        return delegate.eval(prepareScript(readScript(reader), "<eval>"), bindings);
    }

    @Override
    public void put(String key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public Object get(String key) {
        return delegate.get(key);
    }

    @Override
    public Bindings getBindings(int scope) {
        return delegate.getBindings(scope);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        delegate.setBindings(bindings, scope);
    }

    @Override
    public Bindings createBindings() {
        return delegate.createBindings();
    }

    @Override
    public ScriptContext getContext() {
        return delegate.getContext();
    }

    @Override
    public void setContext(ScriptContext context) {
        delegate.setContext(context);
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        return wrapCompiledScript(asCompilable().compile(prepareScript(script, "<compiled>")));
    }

    @Override
    public CompiledScript compile(Reader script) throws ScriptException {
        return wrapCompiledScript(asCompilable().compile(prepareScript(readScript(script), "<compiled>")));
    }

    @Override
    public Object invokeMethod(Object object, String methodName, Object... arguments) throws ScriptException, NoSuchMethodException {
        return asInvocable().invokeMethod(object, methodName, arguments);
    }

    @Override
    public Object invokeFunction(String name, Object... arguments) throws ScriptException, NoSuchMethodException {
        return asInvocable().invokeFunction(name, arguments);
    }

    @Override
    public <T> T getInterface(Class<T> interfaceClass) {
        return asInvocable().getInterface(interfaceClass);
    }

    @Override
    public <T> T getInterface(Object object, Class<T> interfaceClass) {
        return asInvocable().getInterface(object, interfaceClass);
    }

    private Compilable asCompilable() {
        if (delegate instanceof Compilable compilable) {
            return compilable;
        }

        throw new UnsupportedOperationException("Delegate script engine does not support compilation");
    }

    private Invocable asInvocable() {
        if (delegate instanceof Invocable invocable) {
            return invocable;
        }

        throw new UnsupportedOperationException("Delegate script engine does not support invocation");
    }

    protected String prepareScript(String script, String sourceName) throws ScriptException {
        return script;
    }

    private String readScript(Reader reader) throws ScriptException {
        try {
            var writer = new StringWriter();
            reader.transferTo(writer);

            return writer.toString();
        } catch (IOException exception) {
            throw new ScriptException(exception);
        }
    }

    private CompiledScript wrapCompiledScript(CompiledScript compiledScript) {
        return new CompiledScript() {

            @Override
            public Object eval(ScriptContext context) throws ScriptException {
                return compiledScript.eval(context);
            }

            @Override
            public ScriptEngine getEngine() {
                return JavaScriptPolyglotScriptEngine.this;
            }
        };
    }
}
