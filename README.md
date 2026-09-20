# AetherDrive — Enterprise Cloud Storage Platform (GDrive Clone)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)]()
[![Java](https://img.shields.io/badge/Java-21-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)]()
[![React](https://img.shields.io/badge/React-19-cyan.svg)]()
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)]()
[![Storage](https://img.shields.io/badge/Storage-Local%20Only-success.svg)]()

AetherDrive is a secure, high-performance, self-hosted cloud storage workspace featuring an ultra-modern glassmorphic UI, end-to-end access control, chunked multipart uploads, real-time WebSocket notifications, and strict path-confinement security.

> **Note for DevOps Engineers:** This repository is configured to use **Local Storage Only**. No external cloud providers (AWS S3, Cloudflare R2, GCP) or external payment accounts are required. All storage is managed via local directories or containerized volume mounts.

---

## 1. System Architecture

```
                                +---------------------------+
                                |  Client Browser / Device  |
                                +-------------+-------------+
                                              |
                          HTTP / HTTPS (:3000 | :80)
                                              v
                                +---------------------------+
                                |  Nginx Reverse Proxy &    |
                                |    React 19 Frontend      |
                                +-------------+-------------+
                                              |
                         API Proxied Requests | WebSocket (:8080)
                                              v
                                +---------------------------+
                                |   Spring Boot Backend     |
                                |   (Java 21 / REST API)    |
                                +------+------+------+------+
                                       |      |      |
                 +---------------------+      |      +---------------------+
                 | JDBC (:5432)               | Redis (:6379)              | AMQP (:5672)
                 v                            v                            v
    +------------------------+   +------------------------+   +------------------------+
    | PostgreSQL 16 Database |   |   Redis Cache &        |   | RabbitMQ Event Broker  |
    | (Metadata & Security)  |   |   Distributed Rate     |   | (Async Processing)     |
    +------------------------+   +------------------------+   +------------------------+
                                              |
                                              v
                                 +-------------------------+
                                 |  Local Storage Mount    |
                                 |  (/app/data/storage)    |
                                 +-------------------------+
```

---

## 2. Technology Stack & Prerequisites

### Tech Stack
* **Backend:** Spring Boot 4 (Java 21), Spring Data JPA, Spring Security (Stateless JWT), Spring WebSocket (STOMP), Spring Boot Actuator, Hibernate.
* **Frontend:** React 19, TypeScript, Vite 8, CSS Modules, Lucide React icons.
* **Primary Database:** PostgreSQL 16
* **Caching & Rate Limiting:** Redis 7
* **Message Broker:** RabbitMQ 3 Management
* **Containerization:** Docker & Docker Compose
* **Storage Engine:** Local Filesystem (`LocalStorageService` with strict path canonicalization)

### Prerequisites
* **Docker & Docker Compose** (Recommended — version 24+)
* Or for manual development:
  * **Java Development Kit (JDK):** Version 21
  * **Node.js:** Version 20+ (with npm)
  * **PostgreSQL:** Version 16
  * **Redis:** Version 7
  * **RabbitMQ:** Version 3

---

## 3. Quick Start with Docker Compose (Recommended)

To run the complete production-like stack (Database, Cache, Messaging, Backend, Frontend) with one command:

```bash
# 1. Clone the repository
git clone https://github.com/VishnuReddy2398/GdriveClone.git
cd GdriveClone

# 2. Start all services in detached mode
docker-compose up --build -d

# 3. Verify that all 5 containers are running and healthy
docker-compose ps
```

### Services & Port Mappings
| Service | Container Name | Port | Healthcheck / Endpoint |
|---|---|---|---|
| **Frontend Web App** | `drive-frontend` | `http://localhost:3000` | Nginx reverse proxy serving React build |
| **Backend REST API** | `drive-backend` | `http://localhost:8080` | `/health`, `/actuator/health` |
| **PostgreSQL** | `drive-postgres` | `localhost:5432` | `pg_isready -U driveuser -d drivedb` |
| **Redis Cache** | `drive-redis` | `localhost:6379` | `redis-cli ping` |
| **RabbitMQ Broker** | `drive-rabbitmq` | `localhost:5672`<br>`http://localhost:15672` | Management console (`guest`/`guest`) |

### Stopping Services
```bash
docker-compose down
```
To stop services and remove persistent volumes:
```bash
docker-compose down -v
```

---

## 4. Local Development (Manual Setup)

If you wish to run and debug the application outside of Docker:

### 1. Start Infrastructure Only
```bash
docker-compose up -d postgres redis rabbitmq
```

### 2. Build & Run Backend (Spring Boot)
```bash
# Compile and run unit & regression test suite (H2 in-memory, 100% offline)
./mvnw clean test

# Start the Spring Boot application
./mvnw spring-boot:run
```
The backend starts on `http://localhost:8080`.

### 3. Build & Run Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
The Vite development server runs on `http://localhost:5173`.

---

## 5. Environment Variables & Configuration

Configuration is externalized for **12-Factor App compliance**, allowing the same immutable application artifact (`gdriveclone:1.0.0`) to be deployed across **DEV**, **QA**, and **PROD** environments.

A template is available in `.env.example`:

| Variable | Default Value | Description |
|---|---|---|
| `APP_ENV` | `local` | Environment identifier (`local`, `dev`, `qa`, `prod`) |
| `APP_PORT` | `8080` | Backend listening port |
| `FRONTEND_PORT` | `3000` | Frontend mapped port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `drivedb` | Database name |
| `DB_USER` | `driveuser` | Database user |
| `DB_PASSWORD` | `drivepass` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `STORAGE_TYPE` | `local` | Storage provider (**strictly `local`**) |
| `STORAGE_LOCAL_DIRECTORY` | `/app/data/storage` | Local directory for storing encrypted file shards |
| `JWT_SECRET` | *(256-bit safe)* | Secret key used for HMAC-SHA256 JWT tokens |

---

## 6. Health-Check & Observability Endpoints

The application provides dedicated health check endpoints for load balancers, Kubernetes liveness/readiness probes, and CI/CD validation:

### Simple Health Probe
* **Endpoint:** `GET http://localhost:8080/health` or `GET http://localhost:8080/api/health`
* **Access:** Public (No authentication required)
* **Response:**
```json
{
  "status": "UP"
}
```

### Spring Boot Actuator
* **Healthcheck:** `GET http://localhost:8080/actuator/health`
* **Prometheus Metrics:** `GET http://localhost:8080/actuator/prometheus`
* **Application Info:** `GET http://localhost:8080/actuator/info`

---

## 7. Automated Testing & Verification

The repository contains automated unit and security regression test suites. The tests run against an in-memory database and local test storage directory, ensuring **zero external dependencies** during CI pipeline execution.

Run the test suite:
```bash
./mvnw clean test
```
**Test coverage includes:**
* `SecurityRegressionTests`: Strict authorization, Path Traversal prevention, Cross-Site Scripting (XSS) mitigation, Rate limiting, and WebSocket subscription isolation.
* `LocalStorageServiceTest`: Secure path confinement and prefix validation.
* `JwtUtilSecurityTest`: Production entropy validation.
* `PersonalDriveApplicationTests`: Spring application context initialization.

---

## 8. Enterprise Branching & Release Strategy

This repository adheres to the standard GitFlow enterprise workflow:

```
main (Production releases: tags v1.0.0, v1.0.1)
  │
  ▲
release/1.0.0 (Pre-release hardening & QA verification)
  │
  ▲
develop (Integration branch for staging & continuous integration)
  │
  ▲
feature/* (Feature development: e.g., feature/auth-hardening)
hotfix/*  (Urgent production patches applied directly to main and backported)
```

### Versioning Scheme
Version numbering follows [Semantic Versioning 2.0.0](https://semver.org/):
* `MAJOR.MINOR.PATCH` (e.g., `1.0.0`)
* Container images are tagged by version:
  * `gdriveclone-backend:1.0.0`
  * `gdriveclone-frontend:1.0.0`
  * Rollback strategy: In case of deployment failure in PROD, traffic can be redirected instantly to the previous tag (`gdriveclone-backend:0.9.9`).

---

## 9. DevOps Implementation Roadmap

```
+---------------------------------------------------------------+
|                      CI/CD DELIVERY PIPELINE                  |
+---------------------------------------------------------------+
|  1. Developer Git Push to GitHub                              |
|  2. Jenkins Webhook triggers pipeline                         |
|  3. Compile & Run Automated Tests (./mvnw clean test)          |
|  4. SonarQube / Trivy Security & Vulnerability Scan           |
|  5. Build Multi-Stage Docker Image (gdriveclone:1.0.0)        |
|  6. Push Versioned Artifact to Docker Registry                |
|  7. Ansible Automated Deployment:                             |
|     * Deploy to DEV environment -> Automated Smoke Test      |
|     * Deploy to QA environment  -> Manual QA Sign-off         |
|     * Deploy to PROD environment -> Zero-Downtime Rolling Update|
|  8. Observability & Monitoring:                               |
|     * Prometheus scrapes metrics via /actuator/prometheus     |
|     * Grafana Dashboards for CPU, Memory, JVM & Storage       |
+---------------------------------------------------------------+
```

---

## 10. License & Maintenance

Maintained for enterprise DevOps training and laboratory simulation.
Developed by **Vishnu Reddy**.
