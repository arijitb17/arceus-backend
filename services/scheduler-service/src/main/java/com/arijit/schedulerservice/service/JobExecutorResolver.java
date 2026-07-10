package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobExecutorResolver {

    private final Map<ExecutionTarget, JobExecutor> executorsByTarget;

    public JobExecutorResolver(List<JobExecutor> executors) {
        this.executorsByTarget = executors.stream()
                .collect(Collectors.toUnmodifiableMap(JobExecutor::supports, Function.identity()));
    }

    public JobExecutor resolve(ExecutionTarget target) {
        JobExecutor executor = executorsByTarget.get(target);
        if (executor == null) {
            throw new IllegalStateException("No JobExecutor registered for execution target " + target);
        }
        return executor;
    }
}
