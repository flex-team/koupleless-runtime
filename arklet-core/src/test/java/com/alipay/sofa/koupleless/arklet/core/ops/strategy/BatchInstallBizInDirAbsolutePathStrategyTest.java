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
package com.alipay.sofa.koupleless.arklet.core.ops.strategy;

import com.alipay.sofa.koupleless.arklet.core.common.model.BatchInstallRequest;
import com.alipay.sofa.koupleless.arklet.core.common.model.InstallRequest;
import com.alipay.sofa.koupleless.common.util.OSUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class BatchInstallBizInDirAbsolutePathStrategyTest {

    @Rule
    public TemporaryFolder                                 folder   = new TemporaryFolder();

    private final BatchInstallBizInDirAbsolutePathStrategy strategy = new BatchInstallBizInDirAbsolutePathStrategy();

    @Test
    public void installsWithoutArgsWhenNoProvider() throws Throwable {
        jar("alpha.jar", "alpha", "1.0.0", null);

        Map<Integer, List<InstallRequest>> requests = strategy
            .convertToInstallInput(BatchInstallRequest.builder().bizDirAbsolutePath(dir()).build());

        InstallRequest alpha = requests.get(100).get(0);
        Assert.assertEquals("alpha", alpha.getBizName());
        Assert.assertNull(alpha.getArgs());
    }

    @Test
    public void passesProviderArgsOfEachBiz() throws Throwable {
        File alphaJar = jar("alpha.jar", "alpha", "1.0.0", null);
        File betaJar = jar("nested/beta.jar", "beta", "2.0.0", "10");
        List<String> calls = new ArrayList<>();

        Map<Integer, List<InstallRequest>> requests = strategy
            .convertToInstallInput(BatchInstallRequest.builder().bizDirAbsolutePath(dir())
                .bizArgsProvider((name, version, path) -> {
                    calls.add(name + ":" + version + ":" + path);
                    return new String[] { "--biz=" + name };
                }).build());

        Assert.assertArrayEquals(new Integer[] { 10, 100 }, requests.keySet().toArray());
        InstallRequest beta = requests.get(10).get(0);
        InstallRequest alpha = requests.get(100).get(0);
        Assert.assertArrayEquals(new String[] { "--biz=beta" }, beta.getArgs());
        Assert.assertArrayEquals(new String[] { "--biz=alpha" }, alpha.getArgs());
        Assert.assertEquals(OSUtils.getLocalFileProtocolPrefix() + betaJar.getAbsolutePath(),
            beta.getBizUrl());
        Assert.assertTrue(calls.contains("alpha:1.0.0:" + alphaJar.getAbsolutePath()));
        Assert.assertTrue(calls.contains("beta:2.0.0:" + betaJar.getAbsolutePath()));
        Assert.assertEquals(2, calls.size());
    }

    @Test
    public void providerFailureFailsTheBatch() throws Throwable {
        jar("alpha.jar", "alpha", "1.0.0", null);

        try {
            strategy.convertToInstallInput(BatchInstallRequest.builder().bizDirAbsolutePath(dir())
                .bizArgsProvider((name, version, path) -> {
                    throw new IllegalStateException("no args for " + name);
                }).build());
            Assert.fail("provider failure must fail the batch install");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("no args for alpha", expected.getMessage());
        }
    }

    private String dir() {
        return folder.getRoot().getAbsolutePath();
    }

    private File jar(String path, String bizName, String bizVersion,
                     String priority) throws IOException {
        File file = new File(folder.getRoot(), path);
        file.getParentFile().mkdirs();
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Ark-Biz-Name", bizName);
        attributes.putValue("Ark-Biz-Version", bizVersion);
        if (priority != null) {
            attributes.putValue("priority", priority);
        }
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            out.flush();
        }
        return file;
    }
}
