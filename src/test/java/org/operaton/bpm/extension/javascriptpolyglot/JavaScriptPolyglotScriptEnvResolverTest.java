package org.operaton.bpm.extension.javascriptpolyglot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Spin compatibility globals are removed only for polyglot
 * languages.
 */
class JavaScriptPolyglotScriptEnvResolverTest {

    @Test
    void disablesSpinCompatibilityOnlyForPolyglotLanguages() {
        var resolver = new JavaScriptPolyglotSpinScriptEnvResolver(language -> new String[]{"spin.js"});

        assertThat(resolver.resolve(JavaScriptPolyglotScriptEngineFactory.LANGUAGE_NAME)).isEmpty();
        assertThat(resolver.resolve(TypeScriptPolyglotScriptEngineFactory.LANGUAGE_NAME)).isEmpty();
        assertThat(resolver.resolve("javascript")).containsExactly("spin.js");
        assertThat(resolver.resolve("groovy")).containsExactly("spin.js");
    }
}
