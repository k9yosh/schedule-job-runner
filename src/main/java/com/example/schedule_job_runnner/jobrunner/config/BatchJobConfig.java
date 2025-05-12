package com.example.schedule_job_runnner.jobrunner.config;

import com.example.schedule_job_runnner.jobrunner.listener.CustomJobExecutionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class BatchJobConfig {

    @Bean(name = "asyncTaskExecutor")
    public TaskExecutor asyncTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("spring_batch");
        // executor.setConcurrencyLimit(10); // Optional: configure as needed
        return executor;
    }

    @Bean
    @Primary
    public JobLauncher asyncJobLauncher(JobRepository jobRepository, @Qualifier("asyncTaskExecutor") TaskExecutor taskExecutor) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository); // JobRepository is needed by JobLauncher
        jobLauncher.setTaskExecutor(taskExecutor);
        jobLauncher.afterPropertiesSet(); // Important to call this
        return jobLauncher;
    }

    @Bean
    public Tasklet simulatedTasklet() {
        return (contribution, chunkContext) -> {
            String jobName = chunkContext.getStepContext().getJobName();
            long executionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
            String customJobName = chunkContext.getStepContext().getJobParameters().get("customJobName").toString();
            long durationInSeconds = (Long) chunkContext.getStepContext().getJobParameters().getOrDefault("durationInSeconds", 5L);

            log.info("Tasklet for Batch Job: '{}', Execution ID: {}, Custom Name: '{}' STARTING. Will run for {} seconds.", 
                jobName, executionId, customJobName, durationInSeconds);
            
            try {
                Thread.sleep(durationInSeconds * 1000);
            } catch (InterruptedException e) {
                log.error("Tasklet for job '{}' interrupted.", jobName, e);
                Thread.currentThread().interrupt();
                contribution.getStepExecution().getJobExecution().setStatus(org.springframework.batch.core.BatchStatus.STOPPED);
                contribution.setExitStatus(org.springframework.batch.core.ExitStatus.STOPPED.addExitDescription(e.getMessage()));
                return RepeatStatus.FINISHED;
            }
            
            log.info("Tasklet for Batch Job: '{}', Execution ID: {}, Custom Name: '{}' COMPLETED.", jobName, executionId, customJobName);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet simulatedFailingTasklet() {
        return (contribution, chunkContext) -> {
            String jobName = chunkContext.getStepContext().getJobName();
            long executionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
            String customJobName = chunkContext.getStepContext().getJobParameters().get("customJobName").toString();
            long durationInSeconds = (Long) chunkContext.getStepContext().getJobParameters().getOrDefault("durationInSeconds", 5L);

            log.info("Tasklet for FAILING Batch Job: '{}', Execution ID: {}, Custom Name: '{}' STARTING. Will run for {} seconds then fail.", 
                jobName, executionId, customJobName, durationInSeconds);
            
            try {
                Thread.sleep(durationInSeconds * 1000);
            } catch (InterruptedException e) {
                log.warn("Tasklet for failing job '{}' interrupted during sleep.", jobName, e);
                Thread.currentThread().interrupt();
                contribution.getStepExecution().getJobExecution().setStatus(org.springframework.batch.core.BatchStatus.STOPPED);
                contribution.setExitStatus(org.springframework.batch.core.ExitStatus.STOPPED.addExitDescription("Interrupted during sleep"));
                return RepeatStatus.FINISHED; // Technically finished due to interruption, but marking stopped
            }
            
            log.error("Tasklet for FAILING Batch Job: '{}', Execution ID: {}, Custom Name: '{}' SIMULATING FAILURE.", jobName, executionId, customJobName);
            throw new RuntimeException("Simulated failure for job: " + customJobName);
        };
    }

    @Bean
    public Tasklet simulatedStoppingTasklet() {
        return (contribution, chunkContext) -> {
            String jobName = chunkContext.getStepContext().getJobName();
            long executionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
            String customJobName = chunkContext.getStepContext().getJobParameters().get("customJobName").toString();
            long durationInSeconds = (Long) chunkContext.getStepContext().getJobParameters().getOrDefault("durationInSeconds", 5L);

            log.info("Tasklet for STOPPING Batch Job: '{}', Execution ID: {}, Custom Name: '{}' STARTING. Will run for {} seconds then stop.", 
                jobName, executionId, customJobName, durationInSeconds);
            
            try {
                Thread.sleep(durationInSeconds * 1000);
            } catch (InterruptedException e) {
                log.warn("Tasklet for stopping job '{}' interrupted during sleep.", jobName, e);
                Thread.currentThread().interrupt();
                // Already stopping, just finish
                contribution.getStepExecution().getJobExecution().setStatus(org.springframework.batch.core.BatchStatus.STOPPED);
                contribution.setExitStatus(org.springframework.batch.core.ExitStatus.STOPPED.addExitDescription("Interrupted during sleep while trying to stop"));
                return RepeatStatus.FINISHED;
            }
            
            log.warn("Tasklet for STOPPING Batch Job: '{}', Execution ID: {}, Custom Name: '{}' SIMULATING STOP.", jobName, executionId, customJobName);
            contribution.getStepExecution().getJobExecution().setStatus(org.springframework.batch.core.BatchStatus.STOPPED);
            contribution.setExitStatus(org.springframework.batch.core.ExitStatus.STOPPED.addExitDescription("Simulated stop for job: " + customJobName));
            return RepeatStatus.FINISHED; // Tasklet finished, but job status is set to STOPPED
        };
    }

    @Bean
    public Step simulatedStep(JobRepository jobRepository, 
                              PlatformTransactionManager transactionManager, 
                              Tasklet simulatedTasklet) {
        return new StepBuilder("simulatedStep", jobRepository)
                .tasklet(simulatedTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step simulatedFailingStep(JobRepository jobRepository, 
                                     PlatformTransactionManager transactionManager, 
                                     @Qualifier("simulatedFailingTasklet") Tasklet simulatedFailingTasklet) {
        return new StepBuilder("simulatedFailingStep", jobRepository)
                .tasklet(simulatedFailingTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step simulatedStoppingStep(JobRepository jobRepository, 
                                      PlatformTransactionManager transactionManager, 
                                      @Qualifier("simulatedStoppingTasklet") Tasklet simulatedStoppingTasklet) {
        return new StepBuilder("simulatedStoppingStep", jobRepository)
                .tasklet(simulatedStoppingTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job simulatedJob(JobRepository jobRepository, 
                            @Qualifier("simulatedStep") Step simulatedStep,
                            CustomJobExecutionListener customJobExecutionListener) {
        return new JobBuilder("simulatedJob", jobRepository)
                .listener(customJobExecutionListener)
                .start(simulatedStep)
                .build();
    }

    @Bean
    public Job simulatedJob2(JobRepository jobRepository, 
                             @Qualifier("simulatedStep") Step simulatedStep,
                             CustomJobExecutionListener customJobExecutionListener) {
        return new JobBuilder("simulatedJob2", jobRepository)
                .listener(customJobExecutionListener)
                .start(simulatedStep)
                .build();
    }

    @Bean
    public Job simulatedJob3(JobRepository jobRepository, 
                             @Qualifier("simulatedStep") Step simulatedStep,
                             CustomJobExecutionListener customJobExecutionListener) {
        return new JobBuilder("simulatedJob3", jobRepository)
                .listener(customJobExecutionListener)
                .start(simulatedStep)
                .build();
    }

    @Bean
    public Job simulatedJob4(JobRepository jobRepository, 
                             @Qualifier("simulatedFailingStep") Step simulatedFailingStep,
                             CustomJobExecutionListener customJobExecutionListener) {
        return new JobBuilder("simulatedJob4", jobRepository)
                .listener(customJobExecutionListener)
                .start(simulatedFailingStep)
                .build();
    }

    @Bean
    public Job simulatedJob5(JobRepository jobRepository, 
                             @Qualifier("simulatedStoppingStep") Step simulatedStoppingStep,
                             CustomJobExecutionListener customJobExecutionListener) {
        return new JobBuilder("simulatedJob5", jobRepository)
                .listener(customJobExecutionListener)
                .start(simulatedStoppingStep)
                .build();
    }
} 