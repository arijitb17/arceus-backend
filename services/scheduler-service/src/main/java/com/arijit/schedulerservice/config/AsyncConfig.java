package com.arijit.schedulerservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The pool JobPollerService hands due jobs off to. Deliberately bounded and
 * deliberately a single JVM's thread pool, not a distributed worker fleet -
 * see the module Javadoc for why that's a later concern. {@code queueSize}
 * on GET /jobs/stats and the health endpoint both read straight off this
 * executor, so "how backed up is the scheduler right now" is always an
 * honest, live number rather than something tracked separately by hand.
 */
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
        // A rejected job goes back to SCHEDULED/RETRYING in the DB (see
        // JobDispatchService) rather than being silently swallowed - the
        // caller's own thread just picking it up (CallerRunsPolicy) would
        // make the poll loop itself block, defeating the point of the pool.
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
        // @Async without a named qualifier resolves to this bean too - kept
        // to a single executor since job dispatch is currently the only
        // async work in this service.
        return jobTaskExecutor;
    }
}
