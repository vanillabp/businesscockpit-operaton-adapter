package io.vanillabp.cockpit.operaton.migration;

import org.springframework.data.jpa.repository.JpaRepository;

/** Where the aggregates of the test are kept, on the application's own database. */
public interface MigrationRepository extends JpaRepository<MigrationAggregate, String> {

}
