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
package com.alipay.sofa.koupleless.arklet.core.api;

import java.util.List;
import java.util.stream.Collectors;

import com.alipay.sofa.ark.common.util.EnvironmentUtils;
import com.alipay.sofa.koupleless.arklet.core.api.tunnel.Tunnel;
import com.alipay.sofa.koupleless.arklet.core.api.tunnel.http.HttpTunnel;
import com.alipay.sofa.koupleless.arklet.core.tunnel.custom.MockTunnel;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ApiClientTunnelTest {

    private static final String HTTP_ENABLE   = "koupleless.arklet.http.enable";

    private static final String CUSTOM_TUNNEL = "koupleless.arklet.custom.tunnel.classname";

    private String              previousHttpEnable;

    private String              previousCustomTunnel;

    @Before
    public void save() {
        previousHttpEnable = System.getProperty(HTTP_ENABLE);
        previousCustomTunnel = System.getProperty(CUSTOM_TUNNEL);
        EnvironmentUtils.clearSystemProperty(HTTP_ENABLE);
        EnvironmentUtils.clearSystemProperty(CUSTOM_TUNNEL);
    }

    @After
    public void restore() {
        restore(HTTP_ENABLE, previousHttpEnable);
        restore(CUSTOM_TUNNEL, previousCustomTunnel);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            EnvironmentUtils.clearSystemProperty(key);
        } else {
            EnvironmentUtils.setSystemProperty(key, value);
        }
    }

    @Test
    public void bindsHttpTunnelByDefault() {
        Assert.assertTrue(boundTunnels().contains(HttpTunnel.class));
    }

    @Test
    public void bindsHttpTunnelWhenEnabled() {
        EnvironmentUtils.setSystemProperty(HTTP_ENABLE, "true");
        Assert.assertTrue(boundTunnels().contains(HttpTunnel.class));
    }

    @Test
    public void skipsHttpTunnelWhenDisabled() {
        EnvironmentUtils.setSystemProperty(HTTP_ENABLE, "false");
        Assert.assertFalse(boundTunnels().contains(HttpTunnel.class));
    }

    @Test
    public void keepsCustomTunnelWhenHttpTunnelDisabled() {
        EnvironmentUtils.setSystemProperty(HTTP_ENABLE, "false");
        EnvironmentUtils.setSystemProperty(CUSTOM_TUNNEL, MockTunnel.class.getName());
        List<Class<?>> tunnels = boundTunnels();
        Assert.assertFalse(tunnels.contains(HttpTunnel.class));
        Assert.assertTrue(tunnels.contains(MockTunnel.class));
    }

    private List<Class<?>> boundTunnels() {
        return Guice.createInjector(new ApiClient.TunnelGuiceModule())
            .findBindingsByType(new TypeLiteral<Tunnel>() {
            }).stream().map(binding -> binding.getProvider().get().getClass())
            .collect(Collectors.toList());
    }
}
