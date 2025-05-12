package com.example.schedule_job_runnner.jobrunner.service;

import com.example.schedule_job_runnner.jobrunner.dto.BatchJobExecutionInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final JobExplorer jobExplorer;
    private final Sinks.Many<BatchJobExecutionInfoDTO> jobExecutionSink = Sinks.many().replay().latestOrDefault(null);

    public BatchJobExecutionInfoDTO launchJob(String springBatchJobName, Map<String, Object> jobParametersMap) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException, NoSuchJobException {
        log.info("Attempting to launch Spring Batch job: {} with parameters: {}", springBatchJobName, jobParametersMap);
        try {
            org.springframework.batch.core.Job batchJob = jobRegistry.getJob(springBatchJobName);

            JobParametersBuilder paramsBuilder = new JobParametersBuilder(jobExplorer);
            if (jobParametersMap != null) {
                jobParametersMap.forEach((key, value) -> {
                    if (value instanceof String) {
                        paramsBuilder.addString(key, (String) value);
                    } else if (value instanceof Long) {
                        paramsBuilder.addLong(key, (Long) value);
                    } else if (value instanceof Double) {
                        paramsBuilder.addDouble(key, (Double) value);
                    } else if (value instanceof java.util.Date) {
                        paramsBuilder.addDate(key, (java.util.Date) value);
                    } else if (value instanceof LocalDateTime) { 
                        paramsBuilder.addDate(key, java.util.Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant()));
                    }
                });
            }
            
            if (jobParametersMap != null && !jobParametersMap.containsKey("launchTime")) {
                 paramsBuilder.addLong("launchTime", System.currentTimeMillis());
            } else if (jobParametersMap == null) {
                 paramsBuilder.addLong("launchTime", System.currentTimeMillis());
            }

            JobParameters jobParameters = paramsBuilder.toJobParameters();

            JobExecution jobExecution = jobLauncher.run(batchJob, jobParameters);
            BatchJobExecutionInfoDTO dto = mapToDTO(jobExecution);
            jobExecutionSink.tryEmitNext(dto);
            log.info("Successfully launched job '{}', executionId: {}. Initial DTO emitted.", springBatchJobName, jobExecution.getId());
            return dto;

        } catch (NoSuchJobException e) {
            log.error("Spring Batch Job with name '{}' not found in JobRegistry.", springBatchJobName, e);
            throw e;
        } catch (Exception e) {
            log.error("Error launching Spring Batch Job '{}'.", springBatchJobName, e);
            throw e;
        }
    }

    public Flux<BatchJobExecutionInfoDTO> getJobExecutionUpdates() {
        return jobExecutionSink.asFlux().filter(java.util.Objects::nonNull).share();
    }

    public List<BatchJobExecutionInfoDTO> getRunningJobExecutions(String jobName) {
        Set<JobExecution> executions = jobExplorer.findRunningJobExecutions(jobName);
        return executions.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<BatchJobExecutionInfoDTO> getRecentJobExecutionsForJob(String jobName, int count) {
        // Get ALL job instance IDs for the job name
        List<Long> jobInstanceIds = jobExplorer.getJobInstances(jobName, 0, Integer.MAX_VALUE) // Get all instances
                .stream()
                .map(jobInstance -> jobInstance.getInstanceId())
                .collect(Collectors.toList());

        // Fetch all executions for these instances
        return jobInstanceIds.stream()
            .map(instanceId -> jobExplorer.getJobExecutions(jobExplorer.getJobInstance(instanceId))) // Get executions per instance
            .flatMap(List::stream) // Flatten the list of lists of executions
            .sorted((e1, e2) -> { // Sort all executions by creation time descending
                if (e1.getCreateTime() == null && e2.getCreateTime() == null) return 0;
                if (e1.getCreateTime() == null) return 1; 
                if (e2.getCreateTime() == null) return -1; 
                return e2.getCreateTime().compareTo(e1.getCreateTime());
            })
            .limit(count) // Limit to the requested count AFTER sorting all executions
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public BatchJobExecutionInfoDTO getJobExecutionById(Long executionId) {
        JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
        return jobExecution != null ? mapToDTO(jobExecution) : null;
    }

    public void processJobExecutionUpdate(JobExecution jobExecution) {
        if (jobExecution == null) return;
        BatchJobExecutionInfoDTO dto = mapToDTO(jobExecution);
        jobExecutionSink.tryEmitNext(dto);
        log.info("Job execution update processed and emitted: executionId={}, status={}", dto.getExecutionId(), dto.getStatus());
    }

    private BatchJobExecutionInfoDTO mapToDTO(JobExecution jobExecution) {
        if (jobExecution == null) return null;

        Map<String, Object> jobParams = jobExecution.getJobParameters().getParameters().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
        
        LocalDateTime startTimeDate = jobExecution.getStartTime();
        LocalDateTime endTimeDate = jobExecution.getEndTime();

        return BatchJobExecutionInfoDTO.builder()
                .executionId(jobExecution.getId())
                .jobInstanceId(jobExecution.getJobInstance().getInstanceId())
                .jobName(jobExecution.getJobInstance().getJobName())
                .status(jobExecution.getStatus().toString())
                .startTime(startTimeDate)
                .endTime(endTimeDate)
                .exitCode(jobExecution.getExitStatus() != null ? jobExecution.getExitStatus().getExitCode() : null)
                .exitDescription(jobExecution.getExitStatus() != null ? jobExecution.getExitStatus().getExitDescription() : null)
                .jobParameters(jobParams)
                .build();
    }
} 