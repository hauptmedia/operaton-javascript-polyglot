package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.BpmnParseException;
import org.operaton.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.operaton.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.operaton.bpm.engine.impl.util.xml.Element;

import javax.script.ScriptException;
import java.util.List;

/**
 * Validates inline {@code typescript-polyglot} scripts while BPMN resources are
 * parsed for deployment.
 *
 * <p>The listener deliberately reuses {@link TypeScriptPolyglotTranspiler} so
 * deployment-time and runtime diagnostics stay aligned.</p>
 */
public class TypeScriptPolyglotBpmnParseListener implements BpmnParseListener {

    private static final String ID_ATTRIBUTE = "id";
    private static final String SCRIPT_ELEMENT = "script";
    private static final String SCRIPT_FORMAT_ATTRIBUTE = "scriptFormat";

    private final TypeScriptPolyglotTranspiler transpiler;

    public TypeScriptPolyglotBpmnParseListener() {
        this(new TypeScriptPolyglotTranspiler());
    }

    TypeScriptPolyglotBpmnParseListener(TypeScriptPolyglotTranspiler transpiler) {
        this.transpiler = transpiler;
    }

    @Override
    public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
        validateElement(processElement);
    }

    private void validateElement(Element element) {
        if (PolyglotScriptLanguages.TYPESCRIPT.equals(element.attribute(SCRIPT_FORMAT_ATTRIBUTE))) {
            validateInlineScript(element);
        }

        List<Element> childElements = element.elements();

        if (childElements == null) {
            return;
        }

        childElements.forEach(this::validateElement);
    }

    private void validateInlineScript(Element element) {
        var source = readInlineScript(element);

        if (source == null || source.isBlank()) {
            return;
        }

        try {
            transpiler.transpile(source, createSourceName(element));
        } catch (ScriptException exception) {
            throw new BpmnParseException(createFailureMessage(element, exception), element, exception);
        }
    }

    private String readInlineScript(Element element) {
        var directText = element.getText();

        if (directText != null && !directText.isBlank()) {
            return directText;
        }

        var scriptElement = element.element(SCRIPT_ELEMENT);

        if (scriptElement == null) {
            return null;
        }

        return scriptElement.getText();
    }

    private String createSourceName(Element element) {
        var id = element.attribute(ID_ATTRIBUTE);

        if (id == null || id.isBlank()) {
            return element.getTagName();
        }

        return id;
    }

    private String createFailureMessage(Element element, ScriptException exception) {
        return "Unable to validate typescript-polyglot script in " + describeElement(element) + ": " + exception.getMessage();
    }

    private String describeElement(Element element) {
        var id = element.attribute(ID_ATTRIBUTE);

        if (id == null || id.isBlank()) {
            return element.getTagName();
        }

        return element.getTagName() + " '" + id + "'";
    }
}
