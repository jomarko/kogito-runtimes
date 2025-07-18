package org.kie.kogito.persistence.quarkus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.jbpm.flow.migration.model.MigrationPlan;
import org.jbpm.flow.migration.model.NodeInstanceMigrationPlan;
import org.jbpm.flow.migration.model.ProcessDefinitionMigrationPlan;
import org.jbpm.flow.migration.model.ProcessInstanceMigrationPlan;
import org.kie.kogito.process.MigrationPlanInterface;
import org.kie.kogito.process.MigrationPlanServiceNew;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JDBCMigrationPlanServiceNewImpl implements MigrationPlanServiceNew {

    private static final String ID = "id";
    private static final String SOURCE_PROCESS_ID = "source_process_id";
    private static final String SOURCE_PROCESS_VERSION = "source_process_version";
    private static final String TARGET_PROCESS_ID = "target_process_id";
    private static final String TARGET_PROCESS_VERSION = "target_process_version";
    private static final String NODE_MAPPING = "node_mapping";
    private static final String CREATED_AT = "created_at";

    static final String FIND_MIGRATION_PLAN_BY_ID =
            "SELECT id, source_process_id, source_process_version, target_process_id, target_process_version, node_mapping, created_at FROM migration_plans WHERE id = ?";

    private DataSource dataSource;

    public JDBCMigrationPlanServiceNewImpl() {
    }

    @Inject
    public JDBCMigrationPlanServiceNewImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MigrationPlanInterface findMigrationPlanById(String migrationPlanId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_MIGRATION_PLAN_BY_ID);) {
            statement.setString(1, migrationPlanId);

            List<MigrationPlanInterface> data = new ArrayList<>();
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                data.add(migrationPlanRecordFrom(resultSet));
            }
            resultSet.close();
            if (data.isEmpty()) {
                return null;
            }
            if (data.size() > 1) {
                throw new RuntimeException("Multiple migration plans found with id: " + migrationPlanId);
            }

            return data.get(0);

        } catch (SQLException e) {
            throw new RuntimeException("Error finding all migration plans for id: " + migrationPlanId);
        }
    }

    private static MigrationPlan migrationPlanRecordFrom(final ResultSet rs) {

        try {
            return new MigrationPlan() {
                {
                    setProcessMigrationPlan(new ProcessInstanceMigrationPlan() {
                        {
                            setSourceProcessDefinition(new ProcessDefinitionMigrationPlan(rs.getString(SOURCE_PROCESS_ID), rs.getString(SOURCE_PROCESS_VERSION)));
                            setTargetProcessDefinition(new ProcessDefinitionMigrationPlan(rs.getString(TARGET_PROCESS_ID), rs.getString(TARGET_PROCESS_VERSION)));
                            setNodeInstanceMigrationPlan(Arrays.asList(new ObjectMapper().readValue(rs.getString(NODE_MAPPING), NodeInstanceMigrationPlan[].class)));
                        }
                    });
                }
            };
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error migration unmarshalling", e);
        } catch (SQLException e) {
            throw new RuntimeException("Error migration unmarshalling", e);
        }
    }

}
