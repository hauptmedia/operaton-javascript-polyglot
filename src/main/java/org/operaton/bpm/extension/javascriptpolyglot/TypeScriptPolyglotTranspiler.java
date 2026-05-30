package org.operaton.bpm.extension.javascriptpolyglot;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Transpiles TypeScript scripts to JavaScript inside the JVM.
 *
 * <p>The implementation loads the official TypeScript compiler from the
 * WebJar dependency into an isolated GraalJS context. It intentionally runs in
 * transpile-only mode: BPMN scripts get TypeScript syntax support without
 * requiring a Node.js runtime or a project-wide {@code tsconfig.json}.</p>
 */
public class TypeScriptPolyglotTranspiler {

    public static final String TYPESCRIPT_VERSION = "5.9.3";

    private static final String TYPESCRIPT_COMPILER_RESOURCE =
            "META-INF/resources/webjars/typescript/" + TYPESCRIPT_VERSION + "/lib/typescript.js";
    private static final String DEFAULT_SOURCE_NAME = "operaton-script.ts";
    private static final ThreadLocal<Compiler> COMPILERS = ThreadLocal.withInitial(Compiler::new);

    public String transpile(String source, String sourceName) throws ScriptException {
        try {
            return COMPILERS.get().transpile(source, normalizeSourceName(sourceName));
        } catch (PolyglotException | IllegalStateException exception) {
            throw new ScriptException("Unable to transpile TypeScript: " + exception.getMessage());
        }
    }

    private String normalizeSourceName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return DEFAULT_SOURCE_NAME;
        }

        return sourceName.endsWith(".ts") ? sourceName : sourceName + ".ts";
    }

    private static class Compiler {

        private static final String TRANSPILER_BOOTSTRAP = """
                (function(source, sourceName) {
                  var result = ts.transpileModule(String(source), {
                    fileName: sourceName || 'operaton-script.ts',
                    compilerOptions: {
                      target: ts.ScriptTarget.ES2022,
                      module: ts.ModuleKind.None,
                      sourceMap: false,
                      inlineSourceMap: false,
                      inlineSources: false,
                      removeComments: false
                    },
                    reportDiagnostics: true
                  });
                  var diagnostics = (result.diagnostics || [])
                    .filter(function(diagnostic) {
                      return diagnostic.category === ts.DiagnosticCategory.Error;
                    })
                    .map(function(diagnostic) {
                      var position = diagnostic.file && typeof diagnostic.start === 'number'
                        ? diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start)
                        : null;

                      return {
                        code: diagnostic.code,
                        message: ts.flattenDiagnosticMessageText(diagnostic.messageText, '\\n'),
                        line: position ? position.line + 1 : -1,
                        column: position ? position.character + 1 : -1
                      };
                    });

                  return {
                    ok: diagnostics.length === 0,
                    diagnostics: diagnostics,
                    outputText: result.outputText
                  };
                })
                """;

        private final Context context;
        private final Value transpileFunction;

        Compiler() {
            context = Context.newBuilder("js").build();
            loadTypeScriptCompiler(context);
            transpileFunction = context.eval("js", TRANSPILER_BOOTSTRAP);
        }

        String transpile(String source, String sourceName) throws ScriptException {
            var result = transpileFunction.execute(source, sourceName);

            if (!result.getMember("ok").asBoolean()) {
                throw createScriptException(sourceName, result.getMember("diagnostics"));
            }

            return result.getMember("outputText").asString();
        }

        private void loadTypeScriptCompiler(Context context) {
            var classLoader = TypeScriptPolyglotTranspiler.class.getClassLoader();

            try (var inputStream = classLoader.getResourceAsStream(TYPESCRIPT_COMPILER_RESOURCE)) {
                if (inputStream == null) {
                    throw new IllegalStateException("TypeScript compiler resource not found: " + TYPESCRIPT_COMPILER_RESOURCE);
                }

                try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    context.eval(Source.newBuilder("js", reader, TYPESCRIPT_COMPILER_RESOURCE).build());
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load TypeScript compiler", exception);
            }
        }

        private ScriptException createScriptException(String sourceName, Value diagnostics) {
            var parsedDiagnostics = readDiagnostics(diagnostics);

            if (parsedDiagnostics.isEmpty()) {
                return new ScriptException("Unable to transpile TypeScript");
            }

            var message = String.join("\n", parsedDiagnostics.stream().map(Diagnostic::format).toList());
            var firstDiagnostic = parsedDiagnostics.getFirst();

            if (firstDiagnostic.line > 0 && firstDiagnostic.column > 0) {
                return new ScriptException(message, sourceName, firstDiagnostic.line, firstDiagnostic.column);
            }

            return new ScriptException(message);
        }

        private List<Diagnostic> readDiagnostics(Value diagnostics) {
            var parsedDiagnostics = new ArrayList<Diagnostic>();

            for (long index = 0; index < diagnostics.getArraySize(); index++) {
                var diagnostic = diagnostics.getArrayElement(index);
                parsedDiagnostics.add(new Diagnostic(
                        readInt(diagnostic, "code"),
                        readString(diagnostic, "message"),
                        readInt(diagnostic, "line"),
                        readInt(diagnostic, "column")
                ));
            }

            return parsedDiagnostics;
        }

        private String readString(Value value, String memberName) {
            var member = value.getMember(memberName);

            if (member == null || member.isNull()) {
                return "";
            }

            return member.asString();
        }

        private int readInt(Value value, String memberName) {
            var member = value.getMember(memberName);

            if (member == null || member.isNull() || !member.fitsInInt()) {
                return -1;
            }

            return member.asInt();
        }
    }

    private record Diagnostic(int code, String message, int line, int column) {

        String format() {
            var position = line > 0 && column > 0 ? line + ":" + column + " " : "";

            return position + "TS" + code + ": " + message;
        }
    }
}
