package com.example.schedule_job_runnner.jobrunner.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class BatchJobExecutionInfoDTO {
    private Long executionId;
    private Long jobInstanceId;
    private String jobName;
    private String status; // org.springframework.batch.core.BatchStatus.toString()
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String exitCode; // org.springframework.batch.core.ExitStatus.getExitCode()
    private String exitDescription; // org.springframework.batch.core.ExitStatus.getExitDescription()
    private Map<String, Object> jobParameters;
} 