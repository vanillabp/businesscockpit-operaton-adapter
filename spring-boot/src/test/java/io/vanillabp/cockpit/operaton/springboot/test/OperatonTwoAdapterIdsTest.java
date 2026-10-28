package io.vanillabp.cockpit.operaton.springboot.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.operaton.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import io.vanillabp.cockpit.extension.spi.BusinessCockpitBpmsBridge;
import io.vanillabp.cockpit.extension.test.support.CockpitServer;
import io.vanillabp.cockpit.operaton.OperatonUserTaskListener;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.OperatonAdapter;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;

/**
 * Tests two configured Operaton adapter ids side by side, as in a migration between two
 * engines. Each engine gets its own cockpit listeners and its own bridge, and a workflow which
 * runs on one of them is reported once.
 */
@SpringBootTest(classes = {
    TestApplication.class, OperatonTwoAdapterIdsTest.SecondEngineConfiguration.class
}, properties = {
    "vanillabp.prioritized-adapters=c7,c7b", "vanillabp.adapters.c7b.type=operaton", "vanillabp.adapters.c7b.name-clash-avoidance=by-adapter", "vanillabp.adapters.c7b.data-source-name=c7bDataSource", "vanillabp.workflow-modules.c7-cockpit.adapters.c7b.resources-location=classpath*:c7-cockpit/processes"
})
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
@DirtiesContext
public class OperatonTwoAdapterIdsTest {

  /**
   * The datasource the second engine runs on. {@code defaultCandidate = false} keeps it out of
   * injection by type. That is the usual way to add a second datasource to an application.
   */
  @TestConfiguration
  public static class SecondEngineConfiguration {

    @Bean(defaultCandidate = false)
    public DataSource c7bDataSource() {

      return new SimpleDriverDataSource(
          new org.h2.Driver(), "jdbc:h2:mem:c7-cockpit-second-engine;DB_CLOSE_DELAY=-1");

    }

  }

  @DynamicPropertySource
  static void cockpitServer(
      final DynamicPropertyRegistry registry) {

    registry.add("vanillabp.cockpit.rest.base-url", CockpitServer::baseUrl);

  }

  @Autowired
  private TestWorkflowService workflowService;

  @Autowired
  private TransactionTemplate transactions;

  @Autowired
  private ObjectProvider<BusinessCockpitBpmsBridge> bridges;

  @Autowired
  private ObjectProvider<OperatonEngineHolder> engines;

  @Test
  @DisplayName("Each configured Operaton adapter id gets a bridge of its own")
  public void everyAdapterIdHasItsOwnBridge() {

    final var byAdapterId = bridges
        .stream()
        .collect(
            java.util.stream.Collectors
                .toMap(BusinessCockpitBpmsBridge::adapterId, BusinessCockpitBpmsBridge::adapterType));

    assertEquals(Set.of("c7", "c7b"), byAdapterId.keySet());
    byAdapterId
        .values()
        .forEach(adapterType -> assertEquals(OperatonAdapter.ADAPTER_TYPE, adapterType));

  }

  @Test
  @DisplayName("Each engine carries the cockpit's listeners")
  public void everyEngineCarriesTheListeners() {

    final var enginesById = engines.stream().toList();
    assertEquals(2, enginesById.size(), "two adapter ids are two engines");
    assertNotEquals(
        enginesById.get(0).getProcessEngine(),
        enginesById.get(1).getProcessEngine(),
        "both adapter ids ended up on one engine");

    enginesById
        .forEach(
            engine -> assertInstanceOf(
                OperatonUserTaskListener.class,
                createListenersOf(engine.getProcessEngine()).getLast(),
                "the cockpit's listener is missing on the engine of '%s'"
                    .formatted(engine.getAdapterId())));

  }

  @Test
  @DisplayName("A workflow running on one engine is reported once, not once per engine")
  public void aWorkflowIsReportedOnce() {

    CockpitServer.forgetRequests();

    final var started = transactions
        .execute(status -> {
          final var aggregate = new TestAggregate();
          aggregate.setCustomer("Nora");
          return workflowService.processes().startWorkflow(aggregate);
        });

    // every case uses the two paths below, so the wait names the case it is about
    CockpitServer
        .awaitRequestOf(
            "/usertask/created", "\"businessId\":\"%s\"".formatted(started.getId()));
    CockpitServer.awaitQuiet();

    assertEquals(
        1,
        CockpitServer.matching("/workflow/created").size(),
        "the workflow was reported more than once: %s"
            .formatted(CockpitServer.received().stream().map(CockpitServer.Request::path).toList()));
    assertEquals(
        1,
        CockpitServer.matching("/usertask/created").size(),
        "the user task was reported more than once: %s"
            .formatted(CockpitServer.received().stream().map(CockpitServer.Request::path).toList()));

  }

  private static List<TaskListener> createListenersOf(
      final ProcessEngine engine) {

    final var definitionId = engine
        .getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(TestWorkflowService.BPMN_PROCESS_ID)
        .latestVersion()
        .singleResult()
        .getId();
    final var deployed = (ProcessDefinitionEntity) engine
        .getRepositoryService()
        .getProcessDefinition(definitionId);
    final var listeners = Objects.requireNonNullElse(((UserTaskActivityBehavior) deployed
        .findActivity(TestWorkflowService.BPMN_TASK_ID)
        .getActivityBehavior())
        .getTaskDefinition()
        .getBuiltinTaskListenersForEvent(TaskListener.EVENTNAME_CREATE), List.<TaskListener>of());
    assertFalse(listeners.isEmpty(), "no built-in CREATE listener at all");
    return listeners;

  }

}
