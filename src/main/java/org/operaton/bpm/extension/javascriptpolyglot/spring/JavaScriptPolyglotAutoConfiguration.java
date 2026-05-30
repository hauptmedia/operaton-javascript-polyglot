package org.operaton.bpm.extension.javascriptpolyglot.spring;

import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.extension.javascriptpolyglot.JavaScriptPolyglotScriptEnvPlugin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that makes the plugin active when the jar is on
 * the application classpath.
 */
@AutoConfiguration
@ConditionalOnClass(ProcessEnginePlugin.class)
public class JavaScriptPolyglotAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JavaScriptPolyglotScriptEnvPlugin.class)
    public JavaScriptPolyglotScriptEnvPlugin javaScriptPolyglotScriptEnvPlugin() {
        return new JavaScriptPolyglotScriptEnvPlugin();
    }
}
