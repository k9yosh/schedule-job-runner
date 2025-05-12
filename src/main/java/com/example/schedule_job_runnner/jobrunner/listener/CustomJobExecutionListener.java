package com.example.schedule_job_runnner.jobrunner.listener;

// import com.example.schedule_job_runnner.jobrunner.service.JobService;
// import lombok.RequiredArgsConstructor; // REMOVE
import com.example.schedule_job_runnner.jobrunner.service.JobService; // Ensure import is present
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired; // ADD
import org.springframework.stereotype.Component;

@Slf4j
@Component
// @RequiredArgsConstructor // REMOVE
public class CustomJobExecutionListener implements JobExecutionListener {

    @Autowired // ADD
    private JobService jobService;

    // Default constructor needed if @RequiredArgsConstructor is removed and no other constructor exists
    public CustomJobExecutionListener() {}

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job '{}' (executionId: {}) is starting.", jobExecution.getJobInstance().getJobName(), jobExecution.getId());
        // Potentially call jobService here if needed, e.g., jobService.processJobExecutionUpdate(jobExecution);
        // to ensure a DTO is emitted for the STARTING/RUNNING state before the job logic begins.
        // For now, relying on launchJob's emission and afterJob for terminal status, but this is a good place for an early update.
        if (jobService != null) { // Good practice to check for null if autowired field might not be set in some test contexts
             jobService.processJobExecutionUpdate(jobExecution); // Emit initial state
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("Job '{}' (executionId: {}) has finished with status: {}.", 
                 jobExecution.getJobInstance().getJobName(), 
                 jobExecution.getId(), 
                 jobExecution.getStatus());
        if (jobService != null) {
            jobService.processJobExecutionUpdate(jobExecution);
        }
    }
} 