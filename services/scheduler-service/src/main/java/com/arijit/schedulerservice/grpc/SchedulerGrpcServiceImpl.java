package com.arijit.schedulerservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.scheduler.*;
import com.arijit.schedulerservice.dto.CreateJobRequest;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.service.JobService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

/**
 * gRPC face of scheduler-service - every RPC calls straight into
 * {@link JobService}, the same engine JobController uses, so REST and
 * gRPC callers get identical validation and behavior. See scheduler.proto
 * for the wire shapes this adapts to/from.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class SchedulerGrpcServiceImpl extends SchedulerGrpcServiceGrpc.SchedulerGrpcServiceImplBase {

    private final JobService jobService;
    private final JobProtoMapper mapper;

    @Override
    public void scheduleJob(ScheduleJobRequest request, StreamObserver<ScheduleJobResponse> responseObserver) {
        CreateJobRequest createRequest = new CreateJobRequest(
                request.getJobName(),
                request.getServiceName(),
                mapper.parseTriggerType(request.getTriggerType()),
                blankToNull(request.getCronExpression()),
                request.getDelayMs() > 0 ? request.getDelayMs() : null,
                request.getFixedRateMs() > 0 ? request.getFixedRateMs() : null,
                request.getFixedDelayMs() > 0 ? request.getFixedDelayMs() : null,
                mapper.parseExecutionTarget(request.getExecutionTarget()),
                blankToNull(request.getCallbackUrl()),
                blankToNull(request.getTargetExchange()),
                blankToNull(request.getTargetRoutingKey()),
                blankToNull(request.getPayload()),
                request.getPriority(),
                request.getMaxRetries() > 0 ? request.getMaxRetries() : null,
                request.getRetryDelayMs() > 0 ? request.getRetryDelayMs() : null,
                request.getBackoffMultiplier() > 0 ? request.getBackoffMultiplier() : null,
                request.getExponentialBackoff(),
                blankToNull(request.getCorrelationId()),
                request.getExpiresAtEpochMillis() > 0 ? java.time.Instant.ofEpochMilli(request.getExpiresAtEpochMillis()) : null
        );

        Job job = jobService.create(createRequest);
        responseObserver.onNext(ScheduleJobResponse.newBuilder().setJob(mapper.toProto(job)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void cancelJob(JobIdRequest request, StreamObserver<JobActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> jobService.cancel(parseId(request.getId())), "Job cancelled");
    }

    @Override
    public void pauseJob(JobIdRequest request, StreamObserver<JobActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> jobService.pause(parseId(request.getId())), "Job paused");
    }

    @Override
    public void resumeJob(JobIdRequest request, StreamObserver<JobActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> jobService.resume(parseId(request.getId())), "Job resumed");
    }

    @Override
    public void retryJob(JobIdRequest request, StreamObserver<JobActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> jobService.requestRetry(parseId(request.getId())), "Retry requested");
    }

    @Override
    public void getJob(JobIdRequest request, StreamObserver<GetJobResponse> responseObserver) {
        try {
            Job job = jobService.getOrThrow(parseId(request.getId()));
            responseObserver.onNext(GetJobResponse.newBuilder().setFound(true).setJob(mapper.toProto(job)).build());
        } catch (ApiException e) {
            responseObserver.onNext(GetJobResponse.newBuilder().setFound(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void listJobs(ListJobsRequest request, StreamObserver<ListJobsResponse> responseObserver) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String serviceName = request.getServiceName().isBlank() ? null : request.getServiceName();
        JobStatus status = request.getStatus().isBlank() ? null : JobStatus.valueOf(request.getStatus().toUpperCase());

        var result = jobService.list(serviceName, status, pageable);
        var responseBuilder = ListJobsResponse.newBuilder()
                .setTotalElements(result.getTotalElements())
                .setTotalPages(result.getTotalPages())
                .setPage(result.getNumber())
                .setSize(result.getSize());
        result.getContent().forEach(job -> responseBuilder.addJobs(mapper.toProto(job)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("scheduler-service").build());
        responseObserver.onCompleted();
    }

    private void respondWithAction(StreamObserver<JobActionResponse> responseObserver, java.util.function.Supplier<Job> action, String successMessage) {
        try {
            Job job = action.get();
            responseObserver.onNext(JobActionResponse.newBuilder()
                    .setSuccess(true).setMessage(successMessage).setJob(mapper.toProto(job)).build());
        } catch (ApiException e) {
            responseObserver.onNext(JobActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException("'" + id + "' is not a valid job id", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
