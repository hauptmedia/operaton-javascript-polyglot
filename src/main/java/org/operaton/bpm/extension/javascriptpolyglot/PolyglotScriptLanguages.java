package org.operaton.bpm.extension.javascriptpolyglot;

import java.util.Set;

/**
 * Central registry for the opt-in script language names handled by this
 * extension.
 *
 * <p>Keeping the names in one place avoids accidental drift between the
 * JSR-223 factories, Operaton binding selection and Spin environment filtering.</p>
 */
public final class PolyglotScriptLanguages {

    public static final String JAVASCRIPT = "javascript-polyglot";
    public static final String TYPESCRIPT = "typescript-polyglot";

    private static final Set<String> LANGUAGES = Set.of(JAVASCRIPT, TYPESCRIPT);

    private PolyglotScriptLanguages() {
    }

    public static boolean isPolyglotLanguage(String languageName) {
        return languageName != null
                && LANGUAGES.stream().anyMatch(language -> language.equalsIgnoreCase(languageName));
    }
}
