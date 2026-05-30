package org.operaton.bpm.extension.javascriptpolyglot;

import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.engine.impl.mock.MocksResolverFactory;
import org.operaton.bpm.engine.impl.scripting.engine.BeansResolverFactory;
import org.operaton.bpm.engine.impl.scripting.engine.ResolverFactory;
import org.operaton.bpm.engine.impl.scripting.engine.VariableScopeResolverFactory;
import org.operaton.bpm.engine.impl.scripting.env.ScriptEnvResolver;
import org.operaton.bpm.engine.impl.scripting.env.ScriptingEnvironment;
import org.operaton.spin.plugin.impl.SpinScriptEnvResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Operaton process engine plugin that wires the opt-in polyglot languages into
 * the scripting environment.
 *
 * <p>The plugin keeps the normal JavaScript languages on Operaton's default
 * bindings and installs mapped JSON/host-object bindings only for
 * {@code javascript-polyglot} and {@code typescript-polyglot}.</p>
 */
public class JavaScriptPolyglotScriptEnvPlugin implements ProcessEnginePlugin {

    private static final String SPRING_BEANS_RESOLVER_FACTORY_CLASS_NAME = "org.operaton.bpm.engine.spring.SpringBeansResolverFactory";

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        // no-op
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        applyScriptConfiguration(processEngineConfiguration);
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {
        if (processEngine.getProcessEngineConfiguration() instanceof ProcessEngineConfigurationImpl processEngineConfiguration) {
            applyScriptConfiguration(processEngineConfiguration);
        }
    }

    private void applyScriptConfiguration(ProcessEngineConfigurationImpl processEngineConfiguration) {
        applyScriptingEngines(processEngineConfiguration);
        processEngineConfiguration.setEnvScriptResolvers(createScriptEnvResolvers(processEngineConfiguration.getEnvScriptResolvers()));
        applyScriptingEnvironment(processEngineConfiguration);
    }

    private void applyScriptingEngines(ProcessEngineConfigurationImpl processEngineConfiguration) {
        var scriptingEngines = processEngineConfiguration.getScriptingEngines();
        var scriptEngineResolver = processEngineConfiguration.getScriptEngineResolver();

        if (scriptEngineResolver == null) {
            return;
        }

        scriptEngineResolver.addScriptEngineFactory(new JavaScriptPolyglotScriptEngineFactory());
        scriptEngineResolver.addScriptEngineFactory(new TypeScriptPolyglotScriptEngineFactory());

        if (scriptingEngines instanceof JavaScriptPolyglotScriptingEngines || scriptingEngines == null) {
            return;
        }

        var defaultResolverFactories = processEngineConfiguration.getResolverFactories();
        var polyglotResolverFactories = createPolyglotResolverFactories(defaultResolverFactories);
        var polyglotScriptingEngines = new JavaScriptPolyglotScriptingEngines(defaultResolverFactories, polyglotResolverFactories, scriptEngineResolver);
        polyglotScriptingEngines.setEnableScriptEngineCaching(scriptingEngines.isEnableScriptEngineCaching());

        processEngineConfiguration.setScriptingEngines(polyglotScriptingEngines);
    }

    private void applyScriptingEnvironment(ProcessEngineConfigurationImpl processEngineConfiguration) {
        var scriptFactory = processEngineConfiguration.getScriptFactory();
        var envScriptResolvers = processEngineConfiguration.getEnvScriptResolvers();
        var scriptingEngines = processEngineConfiguration.getScriptingEngines();

        if (scriptFactory == null || envScriptResolvers == null || scriptingEngines == null) {
            return;
        }

        processEngineConfiguration.setScriptingEnvironment(new ScriptingEnvironment(scriptFactory, envScriptResolvers, scriptingEngines));
    }

    private List<ResolverFactory> createPolyglotResolverFactories(List<ResolverFactory> existingResolverFactories) {
        var valueMapper = new JavaScriptPolyglotValueMapper();

        if (existingResolverFactories == null) {
            var resolverFactories = new ArrayList<ResolverFactory>();
            resolverFactories.add(new MocksResolverFactory());
            resolverFactories.add(new JavaScriptPolyglotVariableScopeResolverFactory(valueMapper));
            resolverFactories.add(new JavaScriptPolyglotBeansResolverFactory(valueMapper));

            return resolverFactories;
        }

        var resolverFactories = new ArrayList<ResolverFactory>();
        var hasVariableScopeResolver = false;
        var hasBeansResolver = false;

        for (var resolverFactory : existingResolverFactories) {
            if (resolverFactory instanceof JavaScriptPolyglotVariableScopeResolverFactory) {
                hasVariableScopeResolver = true;
                resolverFactories.add(resolverFactory);
            } else if (resolverFactory instanceof VariableScopeResolverFactory) {
                hasVariableScopeResolver = true;
                resolverFactories.add(new JavaScriptPolyglotVariableScopeResolverFactory(valueMapper));
            } else if (resolverFactory instanceof JavaScriptPolyglotBeansResolverFactory) {
                hasBeansResolver = true;
                resolverFactories.add(resolverFactory);
            } else if (resolverFactory instanceof JavaScriptPolyglotMappingResolverFactory mappingResolverFactory) {
                if (isBeansResolverFactory(mappingResolverFactory.getDelegate())) {
                    hasBeansResolver = true;
                }

                resolverFactories.add(resolverFactory);
            } else {
                if (isBeansResolverFactory(resolverFactory)) {
                    hasBeansResolver = true;
                }

                resolverFactories.add(new JavaScriptPolyglotMappingResolverFactory(resolverFactory, valueMapper));
            }
        }

        if (!hasVariableScopeResolver) {
            resolverFactories.add(new JavaScriptPolyglotVariableScopeResolverFactory(valueMapper));
        }

        if (!hasBeansResolver) {
            resolverFactories.add(new JavaScriptPolyglotBeansResolverFactory(valueMapper));
        }

        return resolverFactories;
    }

    private boolean isBeansResolverFactory(ResolverFactory resolverFactory) {
        return resolverFactory instanceof BeansResolverFactory
                || SPRING_BEANS_RESOLVER_FACTORY_CLASS_NAME.equals(resolverFactory.getClass().getName());
    }

    private List<ScriptEnvResolver> createScriptEnvResolvers(List<ScriptEnvResolver> existingScriptEnvResolvers) {
        if (existingScriptEnvResolvers == null) {
            return null;
        }

        var scriptEnvResolvers = new ArrayList<ScriptEnvResolver>();

        for (var scriptEnvResolver : existingScriptEnvResolvers) {
            if (scriptEnvResolver instanceof SpinScriptEnvResolver) {
                scriptEnvResolvers.add(new JavaScriptPolyglotSpinScriptEnvResolver(scriptEnvResolver));
            } else {
                scriptEnvResolvers.add(scriptEnvResolver);
            }
        }

        return scriptEnvResolvers;
    }
}
