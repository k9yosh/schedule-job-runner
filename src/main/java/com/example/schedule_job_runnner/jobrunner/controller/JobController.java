package com.example.schedule_job_runnner.jobrunner.controller;

import com.example.schedule_job_runnner.jobrunner.dto.BatchJobExecutionInfoDTO;
import com.example.schedule_job_runnner.jobrunner.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.CrossOrigin;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin
public class JobController {

    private final JobService jobService;

    @PostMapping("/launch/{jobName}")
    public ResponseEntity<?> launchJob(@PathVariable("jobName") String jobName, 
                                       @RequestBody(required = false) LaunchRequest launchRequest) {
        try {
            Map<String, Object> jobParameters = new HashMap<>();
            String customName = (launchRequest != null && launchRequest.getCustomJobName() != null) 
                                ? launchRequest.getCustomJobName() 
                                : jobName + "_run_" + System.currentTimeMillis();
            long duration = (launchRequest != null && launchRequest.getDurationInSeconds() != null) 
                            ? launchRequest.getDurationInSeconds() 
                            : 10L; // Default duration 10 seconds

            jobParameters.put("customJobName", customName);
            jobParameters.put("durationInSeconds", duration);
            // Add launchTime for uniqueness, JobService also adds one if not present
            jobParameters.put("launchTime", System.currentTimeMillis());

            log.info("Received request to launch job: {} with customName: {}, duration: {}s", 
                jobName, customName, duration);

            BatchJobExecutionInfoDTO executionInfo = jobService.launchJob(jobName, jobParameters);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(executionInfo);
        } catch (Exception e) {
            log.error("Error launching job: {}", jobName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error launching job: " + e.getMessage());
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<BatchJobExecutionInfoDTO> streamJobUpdates() {
        return jobService.getJobExecutionUpdates();
    }

    @GetMapping("/{jobName}/recent")
    public ResponseEntity<List<BatchJobExecutionInfoDTO>> getRecentJobs(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "10") int count) {
        try {
            List<BatchJobExecutionInfoDTO> executions = jobService.getRecentJobExecutionsForJob(jobName, count);
            return ResponseEntity.ok(executions);
        } catch (Exception e) {
            log.error("Error fetching recent jobs for {}: {}", jobName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/execution/{id}")
    public ResponseEntity<BatchJobExecutionInfoDTO> getJobExecutionById(@PathVariable Long id) {
        BatchJobExecutionInfoDTO execution = jobService.getJobExecutionById(id);
        if (execution != null) {
            return ResponseEntity.ok(execution);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/dashboard-snapshot")
    public ResponseEntity<List<BatchJobExecutionInfoDTO>> getDashboardSnapshot() {
        try {
            // In a real app, jobNames might come from JobRegistry or a config
            // --- TODO: Make jobNames dynamic --- 
            List<String> jobNames = List.of("simulatedJob", "simulatedJob2", "simulatedJob3", "simulatedJob4"); 
            List<BatchJobExecutionInfoDTO> snapshot = new ArrayList<>();

            for (String jobName : jobNames) {
                // Only add currently running jobs
                try {
                    snapshot.addAll(jobService.getRunningJobExecutions(jobName));
                } catch (Exception e) {
                    log.warn("Could not retrieve running jobs for {}: {}", jobName, e.getMessage());
                }
                // --- REMOVED fetching recent jobs --- 
            }

            // De-duplicate based on executionId - still useful if a job restarts or has multiple steps reporting
            List<BatchJobExecutionInfoDTO> distinctSnapshot = snapshot.stream()
                    .filter(e -> e != null && e.getExecutionId() != null) 
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(BatchJobExecutionInfoDTO::getExecutionId))),
                            ArrayList::new
                    ));
            
            // Sort them by start time descending (most recent first)
            // Null start times will be treated as older
            distinctSnapshot.sort((j1, j2) -> {
                LocalDateTime t1 = j1.getStartTime();
                LocalDateTime t2 = j2.getStartTime();
                if (t1 == null && t2 == null) return 0;
                if (t1 == null) return 1; // nulls (older/not started) go last
                if (t2 == null) return -1; // non-nulls (started) go first
                return t2.compareTo(t1); // Most recent first
            });

            return ResponseEntity.ok(distinctSnapshot);
        } catch (Exception e) {
            log.error("Error fetching dashboard snapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Inner class for launch request body
    @lombok.Data // Using lombok.Data directly for brevity
    private static class LaunchRequest {
        private String customJobName;
        private Long durationInSeconds;
    }
} 