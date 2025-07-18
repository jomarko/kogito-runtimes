/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.flow.migration.model;

import java.util.Collections;
import java.util.Objects;

import org.kie.kogito.process.MigrationPlanInterface;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MigrationPlan implements MigrationPlanInterface {

    private String name;

    private ProcessInstanceMigrationPlan processMigrationPlan;

    public ProcessInstanceMigrationPlan getProcessMigrationPlan() {
        return processMigrationPlan;
    }

    public void setProcessMigrationPlan(ProcessInstanceMigrationPlan processMigrationPlan) {
        this.processMigrationPlan = processMigrationPlan;
    }

    @JsonIgnore
    public ProcessDefinitionMigrationPlan getSource() {
        return this.processMigrationPlan.getSourceProcessDefinition();
    }

    @Override
    public String nodeMappingJson() {
        try {
            return new ObjectMapper().writeValueAsString(processMigrationPlan.getNodeInstanceMigrationPlan() != null ? processMigrationPlan.getNodeInstanceMigrationPlan() : Collections.emptyList());
        } catch (JsonProcessingException e) {
            return "[]"; // return empty array in case of error
        }
    }

    @Override
    public String sourceProcessId() {
        return processMigrationPlan.getSourceProcessDefinition().getProcessId();
    }

    @Override
    public String sourceProcessVersion() {
        return processMigrationPlan.getSourceProcessDefinition().getProcessVersion();

    }

    @Override
    public String targetProcessId() {
        return processMigrationPlan.getTargetProcessDefinition().getProcessId();
    }

    @Override
    public String targetProcessVersion() {
        return processMigrationPlan.getTargetProcessDefinition().getProcessVersion();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "MigrationPlan [name=" + name + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, processMigrationPlan);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MigrationPlan other = (MigrationPlan) obj;
        return Objects.equals(name, other.name) && Objects.equals(processMigrationPlan, other.processMigrationPlan);
    }

}
