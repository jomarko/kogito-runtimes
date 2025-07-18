package org.kie.kogito.persistence.quarkus;

import org.kie.kogito.process.MigrationPlanInterface;
import org.kie.kogito.process.MigrationPlanServiceNew;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PostgresqlMigrationPlanServiceNewPostgresqlImpl implements MigrationPlanServiceNew {

    @Override
    public MigrationPlanInterface findMigrationPlanById(String migrationPlanId) {
        // TODO Auto-generated method stub
        return null;
    }

}
