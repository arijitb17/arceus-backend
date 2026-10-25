package com.arijit.schedulerservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class AsyncConfig {
    private final SchedulerProperties properties;

    @Bean(name = "jobTaskExecutor")
    public ThreadPoolTaskExecutor jobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.worker().corePoolSize());
        executor.setMaxPoolSize(properties.worker().maxPoolSize());
        executor.setQueueCapacity(properties.worker().queueCapacity());
        executor.setThreadNamePrefix("job-worker-");
        executor.setRejectedExecutionHandler((r, e) -> {
            throw new java.util.concurrent.RejectedExecutionException(
                    "Job worker pool exhausted (core=" + e.getCorePoolSize() + ", max=" + e.getMaximumPoolSize()
                            + ", queued=" + e.getQueue().size() + ")");
        });
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskExecutor taskExecutor(ThreadPoolTaskExecutor jobTaskExecutor) {
        return jobTaskExecutor;
    }
}
