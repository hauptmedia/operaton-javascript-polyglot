package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.impl.scripting.env.ScriptEnvResolver;

/**
 * Prevents the Spin JavaScript compatibility script from being injected into
 * the opt-in polyglot languages.
 *
 * <p>The plugin exposes JSON values directly as JavaScript objects, so the Spin
 * helper globals are intentionally not part of this language.</p>
 */
public class JavaScriptPolyglotSpinScriptEnvResolver implements ScriptEnvResolver {

    private static final String[] EMPTY_SCRIPT_ENV = new String[0];

    private final ScriptEnvResolver delegate;

    public JavaScriptPolyglotSpinScriptEnvResolver(ScriptEnvResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] resolve(String language) {
        if (PolyglotScriptLanguages.isPolyglotLanguage(language)) {
            return EMPTY_SCRIPT_ENV;
        }

        return delegate.resolve(language);
    }
}
