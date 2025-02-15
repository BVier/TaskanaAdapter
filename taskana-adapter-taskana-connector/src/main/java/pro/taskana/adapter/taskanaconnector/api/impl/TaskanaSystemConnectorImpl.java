package pro.taskana.adapter.taskanaconnector.api.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pro.taskana.Task;
import pro.taskana.TaskService;
import pro.taskana.TaskState;
import pro.taskana.TaskSummary;
import pro.taskana.TimeInterval;
import pro.taskana.adapter.exceptions.TaskConversionFailedException;
import pro.taskana.adapter.exceptions.TaskCreationFailedException;
import pro.taskana.adapter.exceptions.TaskTerminationFailedException;
import pro.taskana.adapter.systemconnector.api.ReferencedTask;
import pro.taskana.adapter.taskanaconnector.api.TaskanaConnector;
import pro.taskana.exceptions.ClassificationAlreadyExistException;
import pro.taskana.exceptions.ClassificationNotFoundException;
import pro.taskana.exceptions.DomainNotFoundException;
import pro.taskana.exceptions.InvalidArgumentException;
import pro.taskana.exceptions.InvalidOwnerException;
import pro.taskana.exceptions.InvalidStateException;
import pro.taskana.exceptions.InvalidWorkbasketException;
import pro.taskana.exceptions.NotAuthorizedException;
import pro.taskana.exceptions.TaskAlreadyExistException;
import pro.taskana.exceptions.TaskNotFoundException;
import pro.taskana.exceptions.WorkbasketAlreadyExistException;
import pro.taskana.exceptions.WorkbasketNotFoundException;
import pro.taskana.impl.util.LoggerUtils;

@Component
public class TaskanaSystemConnectorImpl implements TaskanaConnector {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskanaSystemConnectorImpl.class);
    static String  REFERENCED_TASK_ID = "referenced_task_id";
    static String  REFERENCED_TASK_VARIABLES = "referenced_task_variables";

    static String  SYSTEM_URL = "system_url";

    @Autowired    
    private TaskService taskService;
    
    @Autowired
    private TaskInformationMapper taskInformationMapper;

    @Override
    public List<ReferencedTask> retrieveCompletedTaskanaTasks(Instant completedAfter) {
        Instant now = Instant.now();
        TimeInterval completedIn = new TimeInterval(completedAfter, now);

        List<TaskSummary> completedTasks = taskService.createTaskQuery()
                                           .stateIn(TaskState.COMPLETED)
                                           .completedWithin(completedIn)
                                           .list();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("in the time interval {} the following taskana tasks were completed {}",
                           completedIn, LoggerUtils.listToString(completedTasks) );
        }

        
        List<ReferencedTask> result = new ArrayList<>();

        for (TaskSummary taskSummary : completedTasks) {
            try {
                Task taskanaTask = taskService.getTask(taskSummary.getTaskId());
                Map<String,String> callbackInfo = taskanaTask.getCallbackInfo();
                if ( callbackInfo != null && callbackInfo.get(REFERENCED_TASK_ID) != null 
                                          && callbackInfo.get(SYSTEM_URL) != null) {
                    result.add(taskInformationMapper.convertToReferencedTask(taskanaTask));
                }
            } catch (TaskNotFoundException | NotAuthorizedException e) {
                LOGGER.error("Caught {} when trying to retrieve completed taskana tasks." , e);
            }
        }
        
        return result;
    }

    @Override
    public void createTaskanaTask(Task taskanaTask) throws TaskCreationFailedException {
        try {
            taskService.createTask(taskanaTask);
       } catch ( NotAuthorizedException | InvalidArgumentException | ClassificationNotFoundException | WorkbasketNotFoundException | TaskAlreadyExistException e) {
            LOGGER.error("Caught Exception {} when creating taskana task {} ", e, taskanaTask); 
            throw new TaskCreationFailedException("Error when creationg a taskana task " + taskanaTask , e);
        } 
    }

    @Override
    public Task convertToTaskanaTask(ReferencedTask camundaTask) throws TaskConversionFailedException {
        try {
            return taskInformationMapper.convertToTaskanaTask(camundaTask);
        } catch (DomainNotFoundException | InvalidWorkbasketException | NotAuthorizedException | WorkbasketNotFoundException
            | WorkbasketAlreadyExistException | ClassificationAlreadyExistException | InvalidArgumentException e) {
            throw new TaskConversionFailedException("Error when converting camunda task " + camundaTask + " to taskana task.", e);
        }
    }

    @Override
    public ReferencedTask convertToReferencedTask(Task task) {
        return taskInformationMapper.convertToReferencedTask(task);
    }

    @Override
    public void terminateTaskanaTask(ReferencedTask referencedTask) throws TaskTerminationFailedException {
        String taskId = null;
        try {
            TaskSummary taskSummary = taskService.createTaskQuery()
                .externalIdIn(referencedTask.getId())
                .single();
            if (taskSummary != null) {
                taskId = taskSummary.getTaskId();
                taskService.forceCompleteTask(taskId);
            }
        } catch (TaskNotFoundException | InvalidOwnerException | InvalidStateException | NotAuthorizedException ex) {
            throw new TaskTerminationFailedException("Task termination failed for task " + taskId, ex);
        }
    }

}
