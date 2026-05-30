package org.operaton.bpm.extension.javascriptpolyglot;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.delegate.VariableScope;
import org.operaton.bpm.engine.impl.scripting.engine.DefaultScriptEngineResolver;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;
import org.operaton.bpm.engine.impl.scripting.engine.VariableScopeResolverFactory;
import org.operaton.bpm.engine.variable.VariableMap;
import org.operaton.bpm.engine.variable.Variables;
import org.operaton.bpm.engine.variable.value.TypedValue;
import org.operaton.spin.Spin;
import org.operaton.spin.json.SpinJsonNode;

import javax.script.Compilable;
import javax.script.ScriptEngineManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the JSR-223 language registration and the JSON binding behavior that
 * BPMN scripts rely on.
 */
class JavaScriptPolyglotScriptEngineTest {

    @Test
    void registersJavascriptPolyglotScriptEngine() {
        var scriptEngine = new ScriptEngineManager().getEngineByName(JavaScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);

        assertThat(scriptEngine).isInstanceOf(JavaScriptPolyglotScriptEngine.class);
        assertThat(scriptEngine.getFactory().getNames()).contains(JavaScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
    }

    @Test
    void registersTypescriptPolyglotScriptEngine() {
        var scriptEngine = new ScriptEngineManager().getEngineByName(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);

        assertThat(scriptEngine).isInstanceOf(TypeScriptPolyglotScriptEngine.class);
        assertThat(scriptEngine.getFactory().getNames()).contains(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
    }

    @Test
    void mapsJsonProcessVariablesToFirstClassJavascriptObjects() throws Exception {
        var variableScope = new TestVariableScope(Map.of(
                "payload", Spin.JSON("{\"fileRef\":{\"path\":\"./original.eml\"},\"items\":[1]}")
        ));
        var scriptEngine = new ScriptEngineManager().getEngineByName(JavaScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
        var scriptingEngines = createScriptingEngines();
        var bindings = scriptingEngines.createBindings(scriptEngine, variableScope);

        var path = scriptEngine.eval("payload.fileRef.path", bindings);
        scriptEngine.eval("payload.fileRef.path = './changed.eml'; payload.items[1] = 2; payload.created = true;", bindings);

        var payload = (SpinJsonNode) variableScope.getVariable("payload");
        assertThat(path).isEqualTo("./original.eml");
        assertThat(payload.prop("fileRef").prop("path").stringValue()).isEqualTo("./changed.eml");
        assertThat(payload.prop("items").elements().get(1).numberValue().intValue()).isEqualTo(2);
        assertThat(payload.prop("created").boolValue()).isTrue();
    }

    @Test
    void keepsJsonBindingsAvailableForCompiledScripts() throws Exception {
        var variableScope = new TestVariableScope(Map.of(
                "payload", Spin.JSON("{\"fileRef\":{\"path\":\"./compiled.eml\"}}")
        ));
        var scriptEngine = new ScriptEngineManager().getEngineByName(JavaScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
        var scriptingEngines = createScriptingEngines();
        var bindings = scriptingEngines.createBindings(scriptEngine, variableScope);
        var compiledScript = ((Compilable) scriptEngine).compile("payload.fileRef.path");

        assertThat(compiledScript.eval(bindings)).isEqualTo("./compiled.eml");
    }

    @Test
    void mapsJsonProcessVariablesToFirstClassTypescriptObjects() throws Exception {
        var variableScope = new TestVariableScope(Map.of(
                "payload", Spin.JSON("{\"fileRef\":{\"path\":\"./typed-original.eml\"},\"items\":[1]}")
        ));
        var scriptEngine = new ScriptEngineManager().getEngineByName(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
        var scriptingEngines = createScriptingEngines();
        var bindings = scriptingEngines.createBindings(scriptEngine, variableScope);

        var path = scriptEngine.eval("""
                interface UploadEvent {
                  fileRef: { path: string }
                  items: number[]
                  created?: boolean
                }
                const typedPayload: UploadEvent = payload
                typedPayload.fileRef.path = './typed-changed.eml'
                typedPayload.items[1] = 2
                typedPayload.created = true
                typedPayload.fileRef.path
                """, bindings);

        var payload = (SpinJsonNode) variableScope.getVariable("payload");
        assertThat(path).isEqualTo("./typed-changed.eml");
        assertThat(payload.prop("fileRef").prop("path").stringValue()).isEqualTo("./typed-changed.eml");
        assertThat(payload.prop("items").elements().get(1).numberValue().intValue()).isEqualTo(2);
        assertThat(payload.prop("created").boolValue()).isTrue();
    }

    @Test
    void keepsJsonBindingsAvailableForCompiledTypescriptScripts() throws Exception {
        var variableScope = new TestVariableScope(Map.of(
                "payload", Spin.JSON("{\"fileRef\":{\"path\":\"./compiled-typescript.eml\"}}")
        ));
        var scriptEngine = new ScriptEngineManager().getEngineByName(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);
        var scriptingEngines = createScriptingEngines();
        var bindings = scriptingEngines.createBindings(scriptEngine, variableScope);
        var compiledScript = ((Compilable) scriptEngine).compile("""
                const typedPayload: { fileRef: { path: string } } = payload
                typedPayload.fileRef.path
                """);

        assertThat(compiledScript.eval(bindings)).isEqualTo("./compiled-typescript.eml");
    }

    @Test
    void reportsTypescriptSyntaxErrorsAsScriptExceptions() {
        var scriptEngine = new ScriptEngineManager().getEngineByName(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME);

        assertThatThrownBy(() -> scriptEngine.eval("const value: string = ;"))
                .isInstanceOf(javax.script.ScriptException.class)
                .hasMessageContaining("TS");
    }

    @Test
    void keepsNormalJavascriptOnDefaultOperatonBindings() {
        var variableScope = new TestVariableScope(Map.of(
                "payload", Spin.JSON("{\"fileRef\":{\"path\":\"./original.eml\"}}")
        ));
        var scriptEngine = new ScriptEngineManager().getEngineByName("javascript");
        var scriptingEngines = createScriptingEngines();
        var bindings = scriptingEngines.createBindings(scriptEngine, variableScope);

        assertThat(bindings.get("payload")).isInstanceOf(SpinJsonNode.class);
    }

    private JavaScriptPolyglotScriptingEngines createScriptingEngines() {
        List<ResolverFactory> defaultResolverFactories = List.of(new VariableScopeResolverFactory());
        List<ResolverFactory> polyglotResolverFactories = List.of(
                new JavaScriptPolyglotVariableScopeResolverFactory(new JavaScriptPolyglotValueMapper())
        );

        return new JavaScriptPolyglotScriptingEngines(
                defaultResolverFactories,
                polyglotResolverFactories,
                new DefaultScriptEngineResolver(new ScriptEngineManager())
        );
    }

    /**
     * Minimal in-memory {@link VariableScope} for exercising resolver behavior.
     */
    private static class TestVariableScope implements VariableScope {

        private final Map<String, Object> variables = new LinkedHashMap<>();

        TestVariableScope(Map<String, Object> variables) {
            this.variables.putAll(variables);
        }

        @Override
        public String getVariableScopeKey() {
            return "execution";
        }

        @Override
        public Map<String, Object> getVariables() {
            return variables;
        }

        @Override
        public VariableMap getVariablesTyped() {
            return Variables.fromMap(variables);
        }

        @Override
        public VariableMap getVariablesTyped(boolean deserializeValues) {
            return getVariablesTyped();
        }

        @Override
        public Map<String, Object> getVariablesLocal() {
            return variables;
        }

        @Override
        public Set<String> getVariableNames() {
            return new LinkedHashSet<>(variables.keySet());
        }

        @Override
        public Set<String> getVariableNamesLocal() {
            return getVariableNames();
        }

        @Override
        public VariableMap getVariablesLocalTyped() {
            return getVariablesTyped();
        }

        @Override
        public VariableMap getVariablesLocalTyped(boolean deserializeValues) {
            return getVariablesTyped();
        }

        @Override
        public Object getVariable(String variableName) {
            return variables.get(variableName);
        }

        @Override
        public Object getVariableLocal(String variableName) {
            return variables.get(variableName);
        }

        @Override
        public <T extends TypedValue> T getVariableTyped(String variableName) {
            return null;
        }

        @Override
        public <T extends TypedValue> T getVariableTyped(String variableName, boolean deserializeValue) {
            return null;
        }

        @Override
        public <T extends TypedValue> T getVariableLocalTyped(String variableName) {
            return null;
        }

        @Override
        public <T extends TypedValue> T getVariableLocalTyped(String variableName, boolean deserializeValue) {
            return null;
        }

        @Override
        public void setVariable(String variableName, Object value) {
            variables.put(variableName, value);
        }

        @Override
        public void setVariableLocal(String variableName, Object value) {
            variables.put(variableName, value);
        }

        @Override
        public void setVariables(Map<String, ? extends Object> variables) {
            this.variables.putAll(variables);
        }

        @Override
        public void setVariablesLocal(Map<String, ? extends Object> variables) {
            this.variables.putAll(variables);
        }

        @Override
        public boolean hasVariables() {
            return !variables.isEmpty();
        }

        @Override
        public boolean hasVariablesLocal() {
            return hasVariables();
        }

        @Override
        public boolean hasVariable(String variableName) {
            return variables.containsKey(variableName);
        }

        @Override
        public boolean hasVariableLocal(String variableName) {
            return variables.containsKey(variableName);
        }

        @Override
        public void removeVariable(String variableName) {
            variables.remove(variableName);
        }

        @Override
        public void removeVariableLocal(String variableName) {
            variables.remove(variableName);
        }

        @Override
        public void removeVariables(Collection<String> variableNames) {
            variableNames.forEach(variables::remove);
        }

        @Override
        public void removeVariablesLocal(Collection<String> variableNames) {
            removeVariables(variableNames);
        }

        @Override
        public void removeVariables() {
            variables.clear();
        }

        @Override
        public void removeVariablesLocal() {
            variables.clear();
        }
    }
}
