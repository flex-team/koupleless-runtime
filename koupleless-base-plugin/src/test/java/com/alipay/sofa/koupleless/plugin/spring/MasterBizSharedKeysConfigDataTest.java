/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.koupleless.plugin.spring;

import com.alipay.sofa.ark.api.ArkClient;
import com.alipay.sofa.ark.container.service.biz.BizManagerServiceImpl;
import com.alipay.sofa.ark.spi.model.Biz;
import com.alipay.sofa.ark.spi.service.biz.BizManagerService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.boot.support.EnvironmentPostProcessorsFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class MasterBizSharedKeysConfigDataTest {

    private static final String MASTER_BIZ_SOURCE = "MasterBiz-Config resource";

    @Rule
    public TemporaryFolder      folder            = new TemporaryFolder();

    private ClassLoader         originalTccl;

    private File                moduleConfigDir;

    @Before
    public void setUp() throws Exception {
        originalTccl = Thread.currentThread().getContextClassLoader();
        ArkClient.setMasterBiz(null);
        StandardEnvironment baseEnvironment = new StandardEnvironment();
        Map<String, Object> baseConfig = new HashMap<>();
        baseConfig.put("ark.common.env.share.keys", "env");
        baseConfig.put("env", "base-env");
        baseConfig.put("base.only", "base");
        baseEnvironment.getPropertySources().addFirst(new MapPropertySource("base", baseConfig));
        new ServerlessEnvironmentPostProcessor().postProcessEnvironment(baseEnvironment,
            new SpringApplication());

        Biz masterBiz = mock(Biz.class);
        doReturn(ClassLoader.getSystemClassLoader()).when(masterBiz).getBizClassLoader();
        ArkClient.setMasterBiz(masterBiz);
        BizManagerService bizManagerService = mock(BizManagerServiceImpl.class);
        Biz moduleBiz = mock(Biz.class);
        doReturn("shared-keys-module").when(moduleBiz).getBizName();
        doReturn(moduleBiz).when(bizManagerService).getBizByClassLoader(any());
        ArkClient.setBizManagerService(bizManagerService);

        moduleConfigDir = folder.newFolder("module");
        write("probe-base-env.properties", "probe.loaded=base-env\n");
        write("probe-module-env.properties", "probe.loaded=module-env\n");
        write("probe-cli-env.properties", "probe.loaded=cli-env\n");
        write("probe-default-env.properties", "probe.loaded=default-env\n");
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalTccl);
        ArkClient.setMasterBiz(null);
        ArkClient.setBizManagerService(null);
    }

    @Test
    public void sharedKeyResolvesARequiredImportOfTheModuleConfig() throws Exception {
        write("application.properties", "spring.config.import=file:" + probe() + "\n");

        ConfigurableEnvironment environment = prepareModuleEnvironment(null, new HashMap<>());

        Assert.assertEquals("base-env", environment.getProperty("probe.loaded"));
        Assert.assertEquals("base-env", environment.getProperty("env"));
        Assert.assertNull(environment.getProperty("base.only"));
    }

    @Test
    public void sharedKeyResolvesAnOptionalImportOfTheModuleConfig() throws Exception {
        write("application.properties", "spring.config.import=optional:file:" + probe() + "\n");

        ConfigurableEnvironment environment = prepareModuleEnvironment(null, new HashMap<>());

        Assert.assertEquals("base-env", environment.getProperty("probe.loaded"));
    }

    @Test
    public void sharedKeyWinsOverTheModuleConfigWhileResolvingAnImportButNotAfterwards() throws Exception {
        write("application.properties",
            "env=module-env\nspring.config.import=file:" + probe() + "\n");

        ConfigurableEnvironment environment = prepareModuleEnvironment(null, new HashMap<>());

        Assert.assertEquals("base-env", environment.getProperty("probe.loaded"));
        Assert.assertEquals("module-env", environment.getProperty("env"));
    }

    @Test
    public void higherPrecedenceSourceWinsOverASharedKeyWhileResolvingAnImport() throws Exception {
        write("application.properties", "spring.config.import=file:" + probe() + "\n");
        Map<String, Object> commandLine = new HashMap<>();
        commandLine.put("env", "cli-env");

        ConfigurableEnvironment environment = prepareModuleEnvironment(null, commandLine);

        Assert.assertEquals("cli-env", environment.getProperty("probe.loaded"));
        Assert.assertEquals("cli-env", environment.getProperty("env"));
    }

    @Test
    public void sharedSourceStaysLastAfterTheDefaultPropertiesOfTheModule() throws Exception {
        write("application.properties", "spring.config.import=file:" + probe() + "\n");
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("env", "default-env");
        defaults.put("module.default", "module-default");
        DefaultPropertiesPropertySource defaultProperties = new DefaultPropertiesPropertySource(
            defaults);

        ConfigurableEnvironment environment = prepareModuleEnvironment(defaultProperties,
            new HashMap<>());

        Assert.assertEquals("default-env", environment.getProperty("probe.loaded"));
        List<String> names = sourceNames(environment);
        Assert.assertEquals(names.toString(), MASTER_BIZ_SOURCE, names.get(names.size() - 1));
        Assert.assertEquals(names.toString(), DefaultPropertiesPropertySource.NAME,
            names.get(names.size() - 2));
        Assert.assertSame(defaultProperties,
            environment.getPropertySources().get(DefaultPropertiesPropertySource.NAME));
        Assert.assertTrue(environment.getPropertySources()
            .get(MASTER_BIZ_SOURCE) instanceof MasterBizPropertySource);
    }

    @Test
    public void sharedSourceIsAddedOnceAfterTheModuleConfig() throws Exception {
        write("application.properties", "spring.config.import=file:" + probe() + "\n");

        ConfigurableEnvironment environment = prepareModuleEnvironment(null, new HashMap<>());

        List<String> names = sourceNames(environment);
        Assert.assertEquals(names.toString(), MASTER_BIZ_SOURCE, names.get(names.size() - 1));
        Assert.assertFalse(names.toString(), names.contains(DefaultPropertiesPropertySource.NAME));
        Assert.assertEquals(names.toString(), 1,
            names.stream().filter(MASTER_BIZ_SOURCE::equals).count());
        String moduleFile = names.stream().filter(name -> name.contains(moduleConfigDir.getPath()))
            .filter(name -> name.contains("application.properties")).findFirst().orElse(null);
        Assert.assertNotNull(names.toString(), moduleFile);
        Assert.assertTrue(names.toString(),
            names.indexOf(moduleFile) < names.indexOf(MASTER_BIZ_SOURCE));
    }

    private ConfigurableEnvironment prepareModuleEnvironment(PropertySource<?> defaultProperties,
                                                             Map<String, Object> commandLine) {
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], originalTccl));
        StandardEnvironment environment = new StandardEnvironment();
        commandLine.put("spring.config.additional-location",
            "optional:file:" + moduleConfigDir.getPath() + "/");
        environment.getPropertySources()
            .addFirst(new MapPropertySource("commandLineArgs", commandLine));
        if (defaultProperties != null) {
            environment.getPropertySources().addLast(defaultProperties);
        }
        ConfigurationPropertySources.attach(environment);
        SpringApplication application = new SpringApplication();
        List<EnvironmentPostProcessor> postProcessors = EnvironmentPostProcessorsFactory
            .fromSpringFactories(Thread.currentThread().getContextClassLoader())
            .getEnvironmentPostProcessors(new DeferredLogs(), new DefaultBootstrapContext());
        for (EnvironmentPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessEnvironment(environment, application);
        }
        return environment;
    }

    private List<String> sourceNames(ConfigurableEnvironment environment) {
        List<String> names = new ArrayList<>();
        environment.getPropertySources().forEach(source -> names.add(source.getName()));
        return names;
    }

    private String probe() {
        return moduleConfigDir.getPath() + "/probe-${env}.properties";
    }

    private void write(String name, String content) throws Exception {
        Files.write(new File(moduleConfigDir, name).toPath(), content.getBytes());
    }
}
