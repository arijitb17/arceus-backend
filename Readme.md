# Arceus Platform

> A production-grade backend engineering platform built from scratch using Java and Spring Boot.

Arceus Platform is a collection of reusable backend services that solve common engineering problems found in modern distributed systems.

Instead of building another CRUD application, this project focuses on creating production-ready infrastructure components that can be reused across any application.

---

## Vision

Build a complete backend ecosystem similar to what companies build internally.

By the end of this project, Arceus Platform will include:

- Authentication Service
- API Gateway
- Distributed Cache Service
- Feature Flag Service
- Notification Service
- Scheduler Service
- Object Storage Service
- Audit Logging Service
- Deployment Service
- Monitoring & Observability

Every service will be independently deployable while also working together as one platform.

---

# Architecture

```

Clients

|
v

API Gateway

|
+----------------------+----------------------+----------------------+
| | | |
v v v v

Authentication Cache Feature Flags Notification

|
v

Scheduler

|
v

Audit Logging

|
v

Monitoring

```

---

# Tech Stack

### Backend

- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- Hibernate

### Database

- PostgreSQL
- Redis

### Messaging

- Kafka (Later)

### DevOps

- Docker
- Docker Compose
- GitHub Actions

### Monitoring

- Prometheus
- Grafana

### Testing

- JUnit
- Mockito
- Testcontainers

---

# Repository Structure

```

arceus-platform/

services/
auth-service/
gateway-service/
cache-service/
notification-service/
scheduler-service/
feature-flag-service/
deployment-service/
monitoring-service/

shared/

docs/

infrastructure/

docker-compose.yml

README.md

```

---

# Development Roadmap

---

# Phase 0 — Project Setup

Goal

- [ ] Create repository
- [ ] Configure Maven
- [ ] Docker Compose
- [ ] PostgreSQL
- [ ] Redis
- [ ] Shared library
- [ ] GitHub Actions
- [ ] Logging configuration

Deliverable

A clean project structure ready for development.

---

# Phase 1 — Authentication Service

Goal

Build a reusable authentication service.

Features

- [ ] User Registration
- [ ] Login
- [ ] JWT Authentication
- [ ] Refresh Tokens
- [ ] Password Encryption
- [ ] Role Based Access
- [ ] User Profile API
- [ ] Logout
- [ ] Global Exception Handling
- [ ] Validation
- [ ] Swagger Documentation
- [ ] Unit Tests

Stretch Goals

- [ ] Google Login
- [ ] GitHub Login
- [ ] Multi-Factor Authentication
- [ ] Email Verification

---

# Phase 2 — API Gateway

Features

- [ ] Route Requests
- [ ] JWT Validation
- [ ] Rate Limiting
- [ ] Request Logging
- [ ] CORS
- [ ] Request Metrics
- [ ] Health Checks

Stretch Goals

- [ ] Circuit Breaker
- [ ] Load Balancer
- [ ] API Keys

---

# Phase 3 — Distributed Cache

Features

- [x] GET
- [x] SET
- [x] DELETE
- [x] TTL
- [x] Statistics
- [x] Cache Namespaces
- [x] Health Monitoring

Stretch Goals

- [ ] L1 Local Cache
- [x] Cache Eviction Policies
- [ ] Distributed Lock

---

# Phase 4 — Notification Service

Features

- [ ] Email
- [ ] SMS
- [ ] Push Notifications
- [ ] Templates
- [ ] Retry Mechanism
- [ ] Scheduling

Stretch Goals

- [ ] Slack
- [ ] Discord
- [ ] WhatsApp

---

# Phase 5 — Scheduler

Features

- [ ] Cron Jobs
- [ ] Delayed Jobs
- [ ] Retry
- [ ] Dead Letter Queue
- [ ] Dashboard

Stretch Goals

- [ ] Distributed Scheduler

---

# Phase 6 — Feature Flag Service

Features

- [ ] Feature Toggle
- [ ] User Targeting
- [ ] Percentage Rollout
- [ ] Environment Management

Stretch Goals

- [ ] A/B Testing
- [ ] Canary Releases

---

# Phase 7 — Audit Logging

Features

- [ ] API Logs
- [ ] User Activity
- [ ] Admin Actions
- [ ] Search
- [ ] Export

---

# Phase 8 — Deployment Platform

Features

- [ ] GitHub Webhooks
- [ ] Docker Build
- [ ] Deploy
- [ ] Rollback
- [ ] Health Checks

Stretch Goals

- [ ] Kubernetes
- [ ] Blue/Green Deployment

---

# Phase 9 — Monitoring

Features

- [ ] Metrics
- [ ] Logs
- [ ] Traces
- [ ] Alerts
- [ ] Dashboards

---

# Future Modules

- [ ] Object Storage
- [ ] Secret Manager
- [ ] Config Server
- [ ] Webhook Platform
- [ ] API Analytics
- [ ] Billing Service
- [ ] Multi-Tenant Support

---

# Learning Goals

This project is designed to master:

- Spring Boot
- Spring Security
- REST API Design
- JWT Authentication
- OAuth2
- PostgreSQL
- Redis
- Docker
- GitHub Actions
- Distributed Systems
- Caching
- Messaging
- Microservices
- Monitoring
- Production Deployment

---

# Project Status

| Module | Status |
|---------|--------|
| Project Setup | ✅ |
| Authentication (Session, RBAC, Super Admin bootstrap, API Keys, Health) | ✅ v1.0.0 frozen |
| Domain Events (RabbitMQ, decoupling Auth from downstream side-effects) | ✅ |
| Notification (POST /notifications, templates, retry, history) | ✅ |
| Audit Logging (GET /audit-logs, consumes every domain event) | ✅ |
| Rate Limiter (POST /check, Sliding Window + Token Bucket) | ✅ |
| API Gateway (REST entry point, gRPC to services, JWT + rate limit + logging + /health) | ✅ v1 |
| Cache (namespaced REST + gRPC cache platform, TTL policies, stats) | ✅ v1 |
| Scheduler (REST + gRPC + RabbitMQ job scheduling: one-time/delayed/cron/recurring, retry+backoff, DLQ) | ✅ v1 |
| Feature Flags | ⏳ |
| Deployment | ⏳ |
| Monitoring | ⏳ |

Docker, CI/CD, and comprehensive test suites are deliberately deferred until every core service above is finished - see the roadmap doc for the full rationale.

---

# Long-Term Goal

Arceus Platform should evolve into a reusable backend framework where new applications can simply plug in authentication, caching, notifications, deployment, and monitoring without rebuilding them from scratch.
