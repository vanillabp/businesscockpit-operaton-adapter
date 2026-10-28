package io.vanillabp.cockpit.operaton.migration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * The one workflow of the test. The same class serves it on Camunda 7 and on Operaton: a
 * workflow service knows nothing about the engine it runs on.
 */
@Service
@WorkflowService(
    workflowAggregateClass = MigrationAggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = MigrationWorkflowService.BPMN_PROCESS_ID))
public class MigrationWorkflowService {

  /** The BPMN process of the test. */
  public static final String BPMN_PROCESS_ID = "MigrationProcess";

  /** The form key of its user task. */
  public static final String TASK_DEFINITION = "approve";

  /** The BPMN element id of its user task. */
  public static final String BPMN_TASK_ID = "Approve";

  /** The id of the waiting user task, per aggregate id. */
  public static final Map<String, String> WAITING_USER_TASKS = new ConcurrentHashMap<>();

  private final ProcessService<MigrationAggregate> processService;

  public MigrationWorkflowService(
      final ProcessService<MigrationAggregate> processService) {

    this.processService = processService;

  }

  @Transactional
  public MigrationAggregate start(
      final MigrationAggregate aggregate) {

    return processService.startWorkflow(aggregate);

  }

  @Transactional
  public void approve(
      final MigrationAggregate aggregate,
      final String taskId) {

    processService.completeUserTask(aggregate, taskId);

  }

  @WorkflowTask(taskDefinition = TASK_DEFINITION)
  public void waitForApproval(
      final MigrationAggregate aggregate,
      @TaskId final String taskId) {

    WAITING_USER_TASKS.put(aggregate.getId(), taskId);

  }

}
