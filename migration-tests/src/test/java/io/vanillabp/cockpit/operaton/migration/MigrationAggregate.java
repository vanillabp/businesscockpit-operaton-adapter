package io.vanillabp.cockpit.operaton.migration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The workflow aggregate of the test. The test sets the id, so it knows the business key of
 * each workflow before it starts.
 */
@Entity
@Table(name = "MIGRATION_AGGREGATE")
public class MigrationAggregate {

  @Id
  private String id;

  public String getId() {

    return id;

  }

  public void setId(
      final String id) {

    this.id = id;

  }

}
