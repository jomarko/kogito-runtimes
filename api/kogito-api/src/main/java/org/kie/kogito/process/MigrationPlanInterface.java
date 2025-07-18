package org.kie.kogito.process;

public interface MigrationPlanInterface {

    String sourceProcessId();

    String targetProcessId();

    String sourceProcessVersion();

    String targetProcessVersion();

    String nodeMappingJson();
}
