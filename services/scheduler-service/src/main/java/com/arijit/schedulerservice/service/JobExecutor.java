package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;

public interface JobExecutor {
    ExecutionTarget supports();

    void execute(Job job) throws Exception;
}
