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
package org.kie.kogito.process.management;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jbpm.flow.serialization.MarshallerContextName;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerService;
import org.jbpm.ruleflow.core.Metadata;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.runtime.HeadersPersistentConfig;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessError;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.services.uow.UnitOfWorkExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class BaseProcessInstanceManagementResource<T> implements ProcessInstanceManagement<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseProcessInstanceManagementResource.class);
    private static final String PROCESS_REQUIRED = "Process id must be given";
    private static final String PROCESS_AND_INSTANCE_REQUIRED = "Process id and Process instance id must be given";
    private static final String PROCESS_NOT_FOUND = "Process with id %s not found";
    private static final String PROCESS_INSTANCE_NOT_FOUND = "Process instance with id %s not found";
    private static final String PROCESS_INSTANCE_NOT_IN_ERROR = "Process instance with id %s is not in error state";

    private Supplier<Processes> processes;

    private Application application;

    public BaseProcessInstanceManagementResource(Processes processes, Application application) {
        this(() -> processes, application);
    }

    public BaseProcessInstanceManagementResource(Supplier<Processes> processes, Application application) {
        this.processes = processes;
        this.application = application;
    }

    public T doGetProcesses() {
        return buildOkResponse(processes.get().processIds());
    }

    public T doGetProcessInfo(String processId) {
        return executeOnProcess(processId, process -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", process.id());
            data.put("name", process.name());
            data.put("type", process.type());
            data.put("version", process.version());
            if (process instanceof Supplier) {
                org.kie.api.definition.process.Process processDefinition = ((Supplier<org.kie.api.definition.process.Process>) process).get();
                Map<String, Object> metadata = processDefinition.getMetaData();
                String description = (String) metadata.get(Metadata.DESCRIPTION);
                if (description != null) {
                    data.put("description", description);
                }
                List<String> annotations = (List<String>) metadata.get(Metadata.ANNOTATIONS);
                if (annotations != null) {
                    data.put("annotations", annotations);
                }
                if (processDefinition instanceof WorkflowProcess) {
                    WorkflowProcess workflowProcess = (WorkflowProcess) processDefinition;
                    workflowProcess.getInputValidator().flatMap(v -> v.schema(JsonNode.class)).ifPresent(s -> data.put("inputSchema", s));
                    workflowProcess.getOutputValidator().flatMap(v -> v.schema(JsonNode.class)).ifPresent(s -> data.put("outputSchema", s));
                }
            }
            return buildOkResponse(data);
        });
    }

    public T doGetProcessNodes(String processId) {
        return executeOnProcess(processId, process -> {
            List<org.kie.api.definition.process.Node> nodes = ((KogitoWorkflowProcess) ((AbstractProcess<?>) process).get()).getNodesRecursively();
            List<Map<String, Object>> list = nodes.stream().map(n -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", n.getId().toExternalFormat());
                data.put("uniqueId", ((Node) n).getUniqueId());
                data.put("nodeDefinitionId", n.getUniqueId());
                data.put("metadata", n.getMetaData());
                data.put("type", n.getClass().getSimpleName());
                data.put("name", n.getName());
                return data;
            }).collect(Collectors.toList());
            return buildOkResponse(list);
        });
    }

    public T doGetInstanceInError(String processId, String processInstanceId) {

        return executeOnInstanceInError(processId, processInstanceId, processInstance -> {
            ProcessError error = processInstance.error().get();

            Map<String, String> data = new HashMap<>();
            data.put("id", processInstance.id());
            data.put("failedNodeId", error.failedNodeId());
            data.put("message", error.errorMessage());

            return buildOkResponse(data);
        });
    }

    public T doGetWorkItemsInProcessInstance(String processId, String processInstanceId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            // use special security policy to bypass auth check as this is management operation
            List<WorkItem> workItems = processInstance.workItems();
            return buildOkResponse(workItems);
        });
    }

    public T doGetProcessInstanceJson(String processId, String processInstanceId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {

            if (processInstance instanceof AbstractProcessInstance) {

                ((AbstractProcessInstance) processInstance).internalLoadProcessInstanceState();

                if (((AbstractProcessInstance) processInstance).internalGetProcessInstance() instanceof WorkflowProcessInstanceImpl) {

                    return buildOkResponse(new String(ProcessInstanceMarshallerService.newBuilder()
                            .withDefaultObjectMarshallerStrategies()
                            .withDefaultListeners()
                            .withContextEntry(MarshallerContextName.MARSHALLER_FORMAT, MarshallerContextName.MARSHALLER_FORMAT_JSON)
                            .withContextEntry(MarshallerContextName.MARSHALLER_HEADERS_CONFIG, HeadersPersistentConfig.of(false, Optional.empty()))
                            .build().marshallProcessInstance(processInstance), StandardCharsets.UTF_8));

                    // BufferedOutputStream out = new BufferedOutputStream(System.out, 8192);

                    // ProtobufProcessMarshallerWriteContext ctxOut = new ProtobufProcessMarshallerWriteContext(out);
                    // ctxOut.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
                    // ctxOut.set(MarshallerContextName.MARSHALLER_PROCESS, processInstance.process());
                    // ctxOut.set(MarshallerContextName.MARSHALLER_FORMAT, MarshallerContextName.MARSHALLER_FORMAT_JSON);

                    // ProtobufProcessInstanceWriter writer = new ProtobufProcessInstanceWriter(ctxOut);

                    // try {
                    //     writer.writeProcessInstance((WorkflowProcessInstanceImpl) ((AbstractProcessInstance) processInstance).internalGetProcessInstance(), out);
                    //     return buildOkResponse(out);
                    // } catch (IOException e) {
                    //     // TODO Auto-generated catch block
                    //     return badRequestResponse(e.getMessage());
                    // }
                } else {
                    return notFoundResponse("Process Instance Not WorkflowProcessInstanceImpl");
                }
            } else {
                return notFoundResponse("Process Instance Not WorkflowProcessInstanceImpl");
            }
        });
    }

    public T doCreateProcessInstanceFromJson(String processId, String jsonProcessInstance) {
        if (processId == null) {
            return badRequestResponse(PROCESS_REQUIRED);
        }

        if (jsonProcessInstance == null || jsonProcessInstance.trim().isEmpty()) {
            return badRequestResponse("JSON process instance data must be provided");
        }

        Process<?> process = processes.get().processById(processId);
        if (process == null) {
            return notFoundResponse(String.format(PROCESS_NOT_FOUND, processId));
        }

        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            try {
                // Unmarshall the JSON to create a process instance
                ProcessInstanceMarshallerService marshaller = ProcessInstanceMarshallerService.newBuilder()
                        .withDefaultObjectMarshallerStrategies()
                        .withDefaultListeners()
                        .withContextEntry(MarshallerContextName.MARSHALLER_FORMAT, MarshallerContextName.MARSHALLER_FORMAT_JSON)
                        .withContextEntry(MarshallerContextName.MARSHALLER_HEADERS_CONFIG, HeadersPersistentConfig.of(false, Optional.empty()))
                        .withContextEntry(MarshallerContextName.MARSHALLER_PROCESS, process)
                        .build();

                ProcessInstance<?> processInstance = marshaller.unmarshallProcessInstance(jsonProcessInstance.getBytes(StandardCharsets.UTF_8), process);

                LOGGER.error("Unmarshalled process instance: {}", processInstance);

                if (processInstance == null) {
                    return badRequestResponse("Unmarshalling returned null process instance");
                }

                if (processInstance instanceof AbstractProcessInstance) {
                    AbstractProcessInstance<?> abstractProcessInstance = (AbstractProcessInstance<?>) processInstance;
                    if (process.instances() instanceof MutableProcessInstances) {
                        MutableProcessInstances mutableInstances = (MutableProcessInstances) process.instances();
                        String newId = UUID.randomUUID().toString();

                        // Replace process instance ID and all work item IDs in the JSON
                        String updatedJson = replaceIdsInJson(jsonProcessInstance, abstractProcessInstance.id(), newId);

                        abstractProcessInstance
                                .internalSetReloadSupplier(marshaller.createdReloadFunction(() -> updatedJson.getBytes(StandardCharsets.UTF_8)));
                        abstractProcessInstance.internalLoadProcessInstanceState();
                        abstractProcessInstance.getProcessRuntime();

                        mutableInstances.create(newId, abstractProcessInstance);

                        abstractProcessInstance.reconnect();

                        // Update the instance to ensure it's properly persisted with its current state
                        mutableInstances.update(newId, abstractProcessInstance);

                        // Fire process events for Data Index
                        try {
                            if (abstractProcessInstance.internalGetProcessInstance() instanceof WorkflowProcessInstanceImpl) {
                                WorkflowProcessInstanceImpl internalInstance = (WorkflowProcessInstanceImpl) abstractProcessInstance.internalGetProcessInstance();
                                org.drools.core.common.InternalKnowledgeRuntime kruntime = internalInstance.getKnowledgeRuntime();
                                if (kruntime != null) {
                                    org.jbpm.process.instance.InternalProcessRuntime processRuntime =
                                            (org.jbpm.process.instance.InternalProcessRuntime) kruntime.getProcessRuntime();

                                    // Fire the after process started event to notify Data Index
                                    processRuntime.getProcessEventSupport().fireAfterProcessStarted(internalInstance, kruntime);
                                    LOGGER.debug("Fired process started event for instance: {}", newId);

                                    // Register all active work items with the work item manager
                                    List<WorkItemNodeInstance> workItemNodeInstances = activeWorkItemNodeInstances(internalInstance);
                                    for (WorkItemNodeInstance workItemNodeInstance : workItemNodeInstances) {
                                        InternalKogitoWorkItem workItem = workItemNodeInstance.getWorkItem();

                                        // Register the work item - this makes it available to the work item manager
                                        workItemNodeInstance.internalRegisterWorkItem();

                                        // Fire node triggered events for Data Index
                                        processRuntime.getProcessEventSupport().fireBeforeNodeTriggered(workItemNodeInstance, kruntime);
                                        processRuntime.getProcessEventSupport().fireAfterNodeTriggered(workItemNodeInstance, kruntime);

                                        LOGGER.debug("Registered work item: {} in node instance: {}",
                                                workItem.getStringId(), workItemNodeInstance.getStringId());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to process restored instance {}: {}", newId, e.getMessage(), e);
                        }

                        LOGGER.error("Process instance created with id: {}, status: {}", newId, abstractProcessInstance.status());
                    } else {
                        LOGGER.error("Process instances is not mutable, cannot create process instance");
                        return badRequestResponse("Process instances is not mutable, cannot create process instance");
                    }

                    final Map<String, Object> response = new HashMap<>();
                    response.put("id", abstractProcessInstance.id());
                    response.put("status", abstractProcessInstance.status());
                    response.put("message", "Process instance created successfully");
                    return buildOkResponse(response);
                } else {
                    return badRequestResponse("Unable to create process instance from provided JSON");
                }
            } catch (Exception e) {
                // Log the full stack trace for debugging
                LOGGER.error("Error creating process instance from JSON", e);

                // Build detailed error message with stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String stackTrace = sw.toString();

                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                if (e.getCause() != null) {
                    errorMessage += " - Cause: " + (e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getCause().getClass().getName());
                }
                errorMessage += "\nStack trace:\n" + stackTrace;

                return badRequestResponse("Error creating process instance from JSON: " + errorMessage);
            }
        });
    }

    public T doRetriggerInstanceInError(String processId, String processInstanceId) {

        return executeOnInstanceInError(processId, processInstanceId, processInstance -> {
            processInstance.error().get().retrigger();

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    public T doSkipInstanceInError(String processId, String processInstanceId) {

        return executeOnInstanceInError(processId, processInstanceId, processInstance -> {
            processInstance.error().get().skip();

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    public T doTriggerNodeInstanceId(String processId, String processInstanceId, String nodeId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            processInstance.triggerNode(nodeId);

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    public T doRetriggerNodeInstanceId(String processId, String processInstanceId, String nodeInstanceId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            processInstance.retriggerNodeInstance(nodeInstanceId);

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    public T doCancelNodeInstanceId(String processId, String processInstanceId, String nodeInstanceId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            processInstance.cancelNodeInstance(nodeInstanceId);

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    public T doGetProcessInstanceTimers(String processId, String processInstanceId) {
        return executeOnProcessInstance(processId, processInstanceId, processInstance -> buildOkResponse(processInstance.timers()));
    }

    public T doGetNodeInstanceTimers(String processId, String processInstanceId, String nodeInstanceId) {
        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            Collection<KogitoNodeInstance> nodeInstances = processInstance.findNodes(nodeInstance -> nodeInstance.getId().equals(nodeInstanceId));

            if (nodeInstances.isEmpty()) {
                return badRequestResponse(String.format("Failure getting timers for node instance '%s' from proces instance '%s', node instance couldn't be found", nodeInstanceId, processInstanceId));
            }

            return buildOkResponse(nodeInstances.iterator().next().timers());
        });
    }

    public T doCancelProcessInstanceId(String processId, String processInstanceId) {

        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            processInstance.abort();

            if (processInstance.status() == ProcessInstance.STATE_ERROR) {
                throw ProcessInstanceExecutionException.fromError(processInstance);
            } else {
                return buildOkResponse(processInstance.variables());
            }
        });
    }

    /*
     * Helper methods
     */
    private T executeOnInstanceInError(String processId, String processInstanceId, Function<ProcessInstance<?>, T> supplier) {
        if (processId == null || processInstanceId == null) {
            return badRequestResponse(PROCESS_AND_INSTANCE_REQUIRED);
        }

        Process<?> process = processes.get().processById(processId);
        if (process == null) {
            return notFoundResponse(String.format(PROCESS_NOT_FOUND, processId));
        }

        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            Optional<? extends ProcessInstance<?>> processInstanceFound = process.instances().findById(processInstanceId);
            if (processInstanceFound.isPresent()) {
                ProcessInstance<?> processInstance = processInstanceFound.get();

                if (processInstance.error().isPresent()) {
                    return supplier.apply(processInstance);
                } else {
                    return badRequestResponse(String.format(PROCESS_INSTANCE_NOT_IN_ERROR, processInstanceId));
                }
            } else {
                return notFoundResponse(String.format(PROCESS_INSTANCE_NOT_FOUND, processInstanceId));
            }
        });
    }

    private T executeOnProcessInstance(String processId, String processInstanceId, Function<ProcessInstance<?>, T> supplier) {
        if (processId == null || processInstanceId == null) {
            return badRequestResponse(PROCESS_AND_INSTANCE_REQUIRED);
        }

        Process<?> process = processes.get().processById(processId);
        if (process == null) {
            return notFoundResponse(String.format(PROCESS_NOT_FOUND, processId));
        }
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            Optional<? extends ProcessInstance<?>> processInstanceFound = process.instances().findById(processInstanceId);
            if (processInstanceFound.isPresent()) {
                ProcessInstance<?> processInstance = processInstanceFound.get();

                return supplier.apply(processInstance);
            } else {
                return notFoundResponse(String.format(PROCESS_INSTANCE_NOT_FOUND, processInstanceId));
            }
        });
    }

    private T executeOnProcess(String processId, Function<Process<?>, T> supplier) {
        if (processId == null) {
            return badRequestResponse(PROCESS_REQUIRED);
        }

        Process<?> process = processes.get().processById(processId);
        if (process == null) {
            return notFoundResponse(String.format(PROCESS_NOT_FOUND, processId));
        }
        return supplier.apply(process);
    }

    protected abstract <R> T buildOkResponse(R body);

    protected abstract T badRequestResponse(String message);

    protected abstract T notFoundResponse(String message);

    public T doUpdateNodeInstanceSla(String processId, String processInstanceId, String nodeInstanceId, SlaPayload sla) {
        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            try {
                processInstance.updateNodeInstanceSla(nodeInstanceId, sla.getExpirationTime());
                Map<String, Object> message = new HashMap<>();
                message.put("message", "Node Instance '" + nodeInstanceId + "' SLA due date successfully updated");
                return buildOkResponse(message);
            } catch (Exception e) {
                return badRequestResponse(e.getMessage());
            }
        });
    }

    public T doUpdateProcessInstanceSla(String processId, String processInstanceId, SlaPayload sla) {
        return executeOnProcessInstance(processId, processInstanceId, processInstance -> {
            try {
                processInstance.updateProcessInstanceSla(sla.getExpirationTime());
                Map<String, Object> message = new HashMap<>();
                message.put("message", "Process Instance '" + processInstanceId + "' SLA due date successfully updated");
                return buildOkResponse(message);
            } catch (Exception e) {
                return badRequestResponse(e.getMessage());
            }
        });
    }

    /**
     * Replaces the process instance ID and all work item IDs in the JSON with new UUIDs.
     * Also removes externalReferenceId fields as they will be set when user tasks are created.
     * This ensures that when creating a new process instance from JSON, all IDs are unique.
     *
     * @param json the original JSON string
     * @param oldProcessInstanceId the old process instance ID to replace
     * @param newProcessInstanceId the new process instance ID
     * @return the updated JSON with all IDs replaced
     */
    private String replaceIdsInJson(String json, String oldProcessInstanceId, String newProcessInstanceId) {
        // First, replace the process instance ID
        String updatedJson = json.replace(oldProcessInstanceId, newProcessInstanceId);

        // Pattern to match workItemId fields in JSON
        // Matches: "workItemId": "uuid-value"
        Pattern workItemIdPattern = Pattern.compile("\"workItemId\"\\s*:\\s*\"([a-f0-9\\-]{36})\"");
        Matcher matcher = workItemIdPattern.matcher(updatedJson);

        // Map to store old work item ID -> new work item ID mappings
        Map<String, String> workItemIdReplacements = new HashMap<>();

        // Find all work item IDs and create new UUIDs for them
        while (matcher.find()) {
            String oldWorkItemId = matcher.group(1);
            if (!workItemIdReplacements.containsKey(oldWorkItemId)) {
                workItemIdReplacements.put(oldWorkItemId, UUID.randomUUID().toString());
            }
        }

        // Replace all work item IDs with their new UUIDs
        for (Map.Entry<String, String> entry : workItemIdReplacements.entrySet()) {
            updatedJson = updatedJson.replace(entry.getKey(), entry.getValue());
        }

        // Remove externalReferenceId fields - they will be set when user tasks are created
        // Pattern matches: "externalReferenceId": "uuid-value",
        updatedJson = updatedJson.replaceAll(",\\s*\"externalReferenceId\"\\s*:\\s*\"[a-f0-9\\-]{36}\"", "");
        // Also handle case where it might be the last field (no trailing comma)
        updatedJson = updatedJson.replaceAll("\"externalReferenceId\"\\s*:\\s*\"[a-f0-9\\-]{36}\",?", "");

        LOGGER.debug("Replaced {} work item IDs and removed externalReferenceId fields in JSON", workItemIdReplacements.size());

        return updatedJson;
    }

    /**
     * Retrieves all active work item node instances (user tasks) from the process instance.
     * This ensures that Data Index receives events for all user tasks in the process instance
     * so they can be properly indexed and queried via GraphQL.
     *
     * @param processInstance the workflow process instance
     * @return list of active work item node instances
     */
    private List<WorkItemNodeInstance> activeWorkItemNodeInstances(WorkflowProcessInstanceImpl processInstance) {

        // Get all node instances recursively
        Collection<org.jbpm.workflow.instance.NodeInstance> nodeInstances = processInstance.getNodeInstances(true);

        final List<WorkItemNodeInstance> activeWorkItemNodeInstances = new ArrayList<>();
        for (org.jbpm.workflow.instance.NodeInstance nodeInstance : nodeInstances) {
            if (nodeInstance instanceof WorkItemNodeInstance) {
                WorkItemNodeInstance workItemNodeInstance = (WorkItemNodeInstance) nodeInstance;
                InternalKogitoWorkItem workItem = workItemNodeInstance.getWorkItem();

                if (workItem != null && workItem.getState() == 1) { // State 1 = ACTIVE
                    activeWorkItemNodeInstances.add(workItemNodeInstance);
                    LOGGER.debug("Found active work item: {} in state: {} with phase: {}",
                            workItem.getStringId(), workItem.getState(), workItem.getPhaseId());
                }
            }
        }

        return activeWorkItemNodeInstances;
    }

}
