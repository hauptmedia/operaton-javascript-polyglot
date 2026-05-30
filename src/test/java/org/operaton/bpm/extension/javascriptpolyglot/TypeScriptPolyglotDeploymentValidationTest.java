package org.operaton.bpm.extension.javascriptpolyglot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that TypeScript diagnostics are raised while BPMN resources are
 * deployed, not only when a process instance reaches the script at runtime.
 */
class TypeScriptPolyglotDeploymentValidationTest {

    private ProcessEngine processEngine;

    @AfterEach
    void closeProcessEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void deploysValidTypescriptScriptTask() {
        processEngine = createProcessEngine();

        var deployment = processEngine.getRepositoryService()
                .createDeployment()
                .addString("valid-typescript.bpmn", createScriptTaskProcess("const value: string = 'ok'\nvalue"))
                .deploy();

        processEngine.getRepositoryService().deleteDeployment(deployment.getId(), true);
    }

    @Test
    void rejectsInvalidTypescriptScriptTaskDuringDeployment() {
        processEngine = createProcessEngine();

        assertThatThrownBy(() -> processEngine.getRepositoryService()
                .createDeployment()
                .addString("invalid-typescript.bpmn", createScriptTaskProcess("const value: string = ;"))
                .deploy())
                .hasMessageContaining("Unable to validate typescript-polyglot script")
                .hasMessageContaining("TS");
    }

    @Test
    void rejectsInvalidNestedTypescriptScriptDuringDeployment() {
        processEngine = createProcessEngine();

        assertThatThrownBy(() -> processEngine.getRepositoryService()
                .createDeployment()
                .addString("invalid-nested-typescript.bpmn", NESTED_SCRIPT_PROCESS)
                .deploy())
                .hasMessageContaining("Unable to validate typescript-polyglot script")
                .hasMessageContaining("TS");
    }

    private ProcessEngine createProcessEngine() {
        var processEngineConfiguration = (ProcessEngineConfigurationImpl)
                ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        processEngineConfiguration.setProcessEngineName("typescript-polyglot-validation-" + UUID.randomUUID());
        processEngineConfiguration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP);
        processEngineConfiguration.setEnforceHistoryTimeToLive(false);
        processEngineConfiguration.setJobExecutorActivate(false);
        processEngineConfiguration.setProcessEnginePlugins(List.of(new JavaScriptPolyglotScriptEnvPlugin()));

        return processEngineConfiguration.buildProcessEngine();
    }

    private String createScriptTaskProcess(String script) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  targetNamespace="https://operaton.org/test">
                  <bpmn:process id="TypescriptValidationProcess" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1">
                      <bpmn:outgoing>Flow_1</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="ScriptTask_1" />
                    <bpmn:scriptTask id="ScriptTask_1" scriptFormat="typescript-polyglot">
                      <bpmn:incoming>Flow_1</bpmn:incoming>
                      <bpmn:outgoing>Flow_2</bpmn:outgoing>
                      <bpmn:script><![CDATA[
                %s
                      ]]></bpmn:script>
                    </bpmn:scriptTask>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="ScriptTask_1" targetRef="EndEvent_1" />
                    <bpmn:endEvent id="EndEvent_1">
                      <bpmn:incoming>Flow_2</bpmn:incoming>
                    </bpmn:endEvent>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(script);
    }

    private static final String NESTED_SCRIPT_PROCESS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:operaton="http://operaton.org/schema/1.0/bpmn"
                              targetNamespace="https://operaton.org/test">
              <bpmn:process id="NestedTypescriptValidationProcess" isExecutable="true">
                <bpmn:extensionElements>
                  <operaton:executionListener event="start">
                    <operaton:script scriptFormat="typescript-polyglot"><![CDATA[
                      const value: string = ;
                    ]]></operaton:script>
                  </operaton:executionListener>
                </bpmn:extensionElements>
                <bpmn:startEvent id="StartEvent_1" />
              </bpmn:process>
            </bpmn:definitions>
            """;
}
