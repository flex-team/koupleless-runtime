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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * Makes the shared keys of the master biz visible to the config data processing of a biz
 * (for example placeholders in {@code spring.config.import}) with the precedence of default
 * properties: every config file of the biz and every higher precedence source wins.
 * <p>
 * Before {@link ConfigDataEnvironmentPostProcessor} runs, the shared source is appended to the
 * {@code defaultProperties} slot, the only slot that config data resolves after the imported
 * files. {@link ServerlessEnvironmentPostProcessor} restores the original
 * {@code defaultProperties} afterwards and registers the shared source as the last source.
 */
public class MasterBizDefaultPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor,
                                                                Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        if (!ServerlessEnvironmentPostProcessor.isBizEnvironment()) {
            return;
        }
        MutablePropertySources propertySources = environment.getPropertySources();
        PropertySource<?> original = propertySources.get(DefaultPropertiesPropertySource.NAME);
        if (original != null && !(original instanceof EnumerablePropertySource)) {
            return;
        }
        MasterBizDefaultPropertiesPropertySource defaults = new MasterBizDefaultPropertiesPropertySource(
            original);
        defaults
            .addPropertySource(ServerlessEnvironmentPostProcessor.createMasterBizPropertySource());
        if (original == null) {
            propertySources.addLast(defaults);
        } else {
            propertySources.replace(DefaultPropertiesPropertySource.NAME, defaults);
        }
    }

    static void restoreDefaultProperties(MutablePropertySources propertySources) {
        PropertySource<?> current = propertySources.get(DefaultPropertiesPropertySource.NAME);
        if (!(current instanceof MasterBizDefaultPropertiesPropertySource)) {
            return;
        }
        PropertySource<?> original = ((MasterBizDefaultPropertiesPropertySource) current).original;
        if (original == null) {
            propertySources.remove(DefaultPropertiesPropertySource.NAME);
        } else {
            propertySources.replace(DefaultPropertiesPropertySource.NAME, original);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    static final class MasterBizDefaultPropertiesPropertySource extends CompositePropertySource {

        private final PropertySource<?> original;

        MasterBizDefaultPropertiesPropertySource(PropertySource<?> original) {
            super(DefaultPropertiesPropertySource.NAME);
            this.original = original;
            if (original != null) {
                addPropertySource(original);
            }
        }
    }
}
