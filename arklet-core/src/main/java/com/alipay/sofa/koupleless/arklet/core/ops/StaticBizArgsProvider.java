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
package com.alipay.sofa.koupleless.arklet.core.ops;

/**
 * Supplies the program arguments of each biz installed from the static biz dir.
 * The arguments reach only that biz's main method. Without a provider a static biz is installed with no arguments.
 */
public interface StaticBizArgsProvider {

    /**
     * @param bizName Ark-Biz-Name of the biz jar
     * @param bizVersion Ark-Biz-Version of the biz jar
     * @param bizAbsolutePath absolute path of the biz jar
     * @return program arguments for the biz, or null for none
     */
    String[] getArgs(String bizName, String bizVersion, String bizAbsolutePath);
}
