# Personal Drive Clone — Complete Project Plan

**Goal:** a self-hosted, Google-Drive-like file storage app for personal use, portfolio/resume, and hands-on Spring Boot system design practice.

**Architecture:** modular monolith (single Spring Boot deployable, clean package-per-domain boundaries), with one async worker service extracted later to demonstrate real microservice decomposition.

---

## 0. Honest correction before you build on it

You said R2 "doesn't have upload/download limits" — that's half right, and worth being precise about since it drives your storage design:

| R2 free tier (2026, recurring monthly, not a trial) | Amount |
|---|---|
| Storage | **10 GB** total, across all buckets |
| Class A operations (writes: PUT, multipart, list) | 1,000,000 / month |
| Class B operations (reads: GET, HEAD) | 10,000,000 / month |
| **Egress (downloads/bandwidth)** | **$0, always, unlimited volume** |

So: uploads and storage *are* capped (10 GB is the wall you'll hit first for a personal drive with photos/videos). What's genuinely unlimited is **egress** — you can download the same file a thousand times and pay nothing, unlike Oracle Object Storage or AWS S3 which meter bandwidth out. That's the real reason R2 is a good fit here: for a personal drive you *read* far more than you write, and reads-out are free forever. If you outgrow 10 GB, overage is cheap ($0.015/GB-month) — a real business would need to plan for this, but for a personal project you'll likely just pay a couple dollars a month once you cross it, no code changes required.

---

## 1. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language/runtime | Java 21, Spring Boot 3.x | Matches your existing stack |
| API | Spring Web (REST) | Standard |
| Auth | Spring Security + JWT | Stateless, scales to a single VM fine |
| Metadata DB | **Choice A or B below** | See Section 3 |
| Object storage | Cloudflare R2 (S3-compatible API) | Free egress, generous read/write caps |
| Async messaging | RabbitMQ (single node, Docker) | Lighter than Kafka for one worker consumer |
| Observability | OpenTelemetry + Prometheus + Grafana | You already know this stack from work |
| Resilience | Resilience4j | Circuit breaker around DB/R2 calls |
| Frontend | React + tus-js-client or Uppy | Resumable uploads, drag-and-drop |
| Compute host | Oracle Cloud Ampere A1 VM (free tier, 2 OCPU/12 GB) | Free 24/7 hosting |
| Reverse proxy/TLS | Caddy | Automatic Let's Encrypt certs, zero config |
| CI/CD | GitHub Actions → SSH deploy | Simple, appropriate at this scale |
| IaC | Terraform | You already know it; used here for provisioning only |

---

## 2. Cloudflare R2 setup (do this first — everything else depends on it)

1. Sign up / log in at Cloudflare, go to **R2 Object Storage** → **Create bucket**. Name it e.g. `personal-drive-storage`.
2. **R2** → **Manage API tokens** → create a token scoped to that bucket with **Object Read & Write** permission. Save the **Access Key ID** and **Secret Access Key** — shown once.
3. Note your **Account ID** (right sidebar of the R2 dashboard). Your S3-compatible endpoint is:
   ```
   https://<ACCOUNT_ID>.r2.cloudflarestorage.com
   ```
4. R2 is S3-API-compatible, so you use the standard AWS SDK for Java, just pointed at a different endpoint:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

```java
@Configuration
public class R2Config {

    @Value("${r2.account-id}") private String accountId;
    @Value("${r2.access-key}") private String accessKey;
    @Value("${r2.secret-key}") private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
    }
}
```

**Local test / outcome:** write a throwaway `CommandLineRunner` that calls `s3Client.putObject(...)` with a test string and then `getObject(...)` to read it back. If you see your test bytes echoed back, R2 wiring is confirmed working before you build anything else on top of it.

---

## 3. Database — both options, so you can choose (or demo both)

Keep your JPA entities vendor-neutral (avoid Oracle- or Postgres-specific column types) so you can swap between these with just a Spring profile.

### Option A — Oracle Autonomous Database (managed, free tier)

**Setup:**
1. OCI Console → **Autonomous Database** → **Create Autonomous Database**. Choose "Always Free", workload type **Transaction Processing**, set admin password.
2. Download the **wallet** (connection credentials zip) from the instance's **DB Connection** page.
3. Unzip the wallet into your project (e.g. `src/main/resources/wallet/`), and reference it via `TNS_ADMIN`.

```yaml
# application-atp.yml
spring:
  datasource:
    url: jdbc:oracle:thin:@<your_db_low>?TNS_ADMIN=/path/to/wallet
    username: ADMIN
    password: ${ATP_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    database-platform: org.hibernate.dialect.OracleDialect
```

**Pros:** doesn't consume your VM's 12 GB RAM budget at all — Oracle runs it separately. **Cons:** wallet-based TLS setup is fiddly the first time; Oracle SQL dialect quirks (e.g. `VARCHAR2`, sequence-based IDs) show up in edge cases.

### Option B — Self-hosted Postgres (Docker container on your VM)

```yaml
# docker-compose.yml (relevant service)
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: drive
      POSTGRES_USER: drive
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"
volumes:
  pgdata:
```

```yaml
# application-postgres.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/drive
    username: drive
    password: ${POSTGRES_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

**Pros:** full control, zero vendor lock-in, identical local/prod behavior, easier to reason about. **Cons:** eats into your 12 GB VM RAM budget alongside the app itself — budget roughly 300-500 MB for Postgres at this scale.

**Recommendation:** use **Option B (Postgres in Docker) for local dev always** — you don't want a wallet dependency slowing down `docker compose up`. For production, pick **A if you want the RAM headroom and a "managed database" resume line**, or **B if you want the "I run and tune my own Postgres" story**. Both are legitimate; make the call and record it, don't run both in prod.

**Local test / outcome:** with either profile active, `./mvnw spring-boot:run` should start clean and `GET /actuator/health` should report the DB component as `UP`.

---

## 4. Local development environment (Step 0)

**Tasks:**
- Install Java 21, Docker + Docker Compose, an IDE.
- `docker-compose.local.yml` bringing up Postgres + RabbitMQ (see Appendix).
- Scaffold the Spring Boot project (Spring Initializr): Web, Security, Data JPA, Validation, Actuator, Postgres driver, Oracle JDBC driver (optional if testing Option A locally too), Lombok.

**Local test / outcome:** `docker compose -f docker-compose.local.yml up -d` starts Postgres + RabbitMQ without errors; `./mvnw spring-boot:run` boots the app; `curl localhost:8080/actuator/health` returns `{"status":"UP"}`.

---

## 5. Step-by-step build plan

Each step lists what to build and exactly how you'll know it's done **before moving on** — don't proceed to the next step until the local outcome checks out.

### Step 1 — Auth (JWT)
**Build:** `User` entity, `/auth/register`, `/auth/login` (issues JWT), a `JwtAuthFilter` validating Bearer tokens on protected routes.
**Local outcome:** `POST /auth/register` creates a user (201); `POST /auth/login` returns a JWT; `GET /api/me` with `Authorization: Bearer <token>` returns your user info (200); same call with no header returns 401.

### Step 2 — Folder & file metadata
**Build:** `Folder` (self-referencing `parentId`), `FileRecord` (name, size, mimeType, folderId, ownerId, storageKey, sha256, createdAt, deletedAt). CRUD + list-children endpoints.
**Local outcome:** create a root folder, create a nested subfolder inside it, `GET /folders/{id}/children` returns the correct tree; soft-delete sets `deletedAt` (row still exists in DB, just filtered out of normal queries).

### Step 3 — Upload/download via R2
**Build:** `POST /files` (multipart) streams the file to R2 under key `{userId}/{uuid}`, computes SHA-256, writes the `FileRecord`. `GET /files/{id}/download-url` returns a short-lived presigned R2 URL.
**Local outcome:** `curl -F file=@test.pdf http://localhost:8080/files` returns a file id; the object is visible in the R2 dashboard under that key; hitting the presigned URL downloads a byte-identical file (`sha256sum` matches).

### Step 4 — Chunked/resumable uploads
**Build:** switch large uploads to R2's multipart API: `CreateMultipartUpload` → client uploads parts → `CompleteMultipartUpload`.
**Local outcome:** upload a 200 MB+ test file in parts (Uppy or a script), interrupt and resume it, confirm the final object in R2 has the correct size and hash.

### Step 5 — Content-addressable dedup
**Build:** before creating a new object, check a `content_hash → storage_key` table; if the hash already exists, link the new `FileRecord` to the existing object instead of re-uploading.
**Local outcome:** upload the same file twice under different names; confirm (via your R2 Class A operation count, or app logs) the second upload skipped the actual `PutObject` call, and both `FileRecord`s reference the same `storageKey`.

### Step 6 — Sharing & ACLs
**Build:** `ShareLink` (token, target file/folder id, expiry, permission). Public `GET /share/{token}` endpoint, no auth required, validates expiry.
**Local outcome:** generate a share link, open it in an incognito window with no login — file downloads; wait past expiry (or set a 5-second expiry for testing) — same link now returns 403.

### Step 7 — Versioning & recycle bin
**Build:** re-uploading the same logical file creates a new version row instead of overwriting; soft-deleted items appear in `/trash`; a scheduled job purges anything older than 30 days.
**Local outcome:** upload the same filename twice, `GET /files/{id}/versions` shows two entries; delete a file, confirm it's listed under `/trash` but not in the normal folder view; call `/trash/{id}/restore` and confirm it reappears in its original folder.

### Step 8 — Search
**Build:** Postgres `tsvector` column on filename (and path), a `GET /search?q=` endpoint ranked by `ts_rank`.
**Local outcome:** search for a partial/misspelled filename and get relevant results ranked sensibly.

### Step 9 — Async worker (the one real microservice)
**Build:** monolith publishes a `FileUploaded` event to RabbitMQ using the transactional outbox pattern (write the event to an `outbox` table in the same DB transaction as the `FileRecord` insert, then a poller publishes it — so you never lose an event to a crash between DB commit and message publish). A **separate** small Spring Boot service consumes the event, generates a thumbnail for images (Thumbnailator) and uploads it to R2 at `{fileId}/thumb.jpg`, and runs a virus scan (ClamAV via `clamscan` CLI or a Java binding).
**Local outcome:** upload a JPEG, within a few seconds see `{fileId}/thumb.jpg` appear in R2 and a `thumbnailUrl` populate on the `FileRecord`; upload the EICAR test string as a file and confirm it gets flagged/quarantined rather than served.

### Step 10 — Observability & resilience
**Build:** OpenTelemetry auto-instrumentation exporting to a local Jaeger container; Micrometer + Prometheus + Grafana dashboard; Resilience4j circuit breaker + retry wrapping your DB and R2 calls.
**Local outcome:** Jaeger UI shows one trace spanning API → DB → R2 for a single upload request; stop the local Postgres container mid-test and confirm the app returns a clean 503 with the circuit breaker opening in logs, rather than hanging or crashing.

### Step 11 — Frontend
**Build:** React file browser — folder navigation, drag-and-drop upload with progress (tus-js-client/Uppy), share dialog, trash view, search bar.
**Local outcome:** full click-through against `localhost:8080` — upload, browse folders, share a link, delete, restore from trash, search — all work without touching curl.

---

## 6. Deployment (once every step above passes locally)

### 6.1 Provision the VM with Terraform

```hcl
resource "oci_core_instance" "drive_vm" {
  compartment_id      = var.compartment_id
  availability_domain = var.ad
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.drive_subnet.id
    assign_public_ip = true
  }

  source_details {
    source_type = "image"
    source_id   = var.ubuntu_image_id
  }

  metadata = {
    ssh_authorized_keys = file(var.ssh_public_key_path)
  }
}

resource "oci_core_security_list" "drive_sl" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.drive_vcn.id

  ingress_security_rules {
    protocol = "6" # TCP
    source   = "0.0.0.0/0"
    tcp_options { min = 443; max = 443 }
  }
  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options { min = 80; max = 80 }
  }
}
```

**Outcome:** `terraform apply` gives you a public IP you can SSH into.

### 6.2 Production Docker Compose (on the VM)

```yaml
services:
  app:
    image: ghcr.io/<you>/drive-app:latest
    restart: unless-stopped
    env_file: .env
    depends_on: [rabbitmq]
  worker:
    image: ghcr.io/<you>/drive-worker:latest
    restart: unless-stopped
    env_file: .env
    depends_on: [rabbitmq]
  rabbitmq:
    image: rabbitmq:3-management
    restart: unless-stopped
  # only include this if you chose Option B for the DB:
  postgres:
    image: postgres:16
    restart: unless-stopped
    env_file: .env
    volumes: [pgdata:/var/lib/postgresql/data]
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
volumes:
  pgdata:
  caddy_data:
```

```
# Caddyfile
yourdomain.com {
    reverse_proxy app:8080
}
```

### 6.3 CI/CD (GitHub Actions)

```yaml
name: deploy
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build and push image
        run: |
          docker build -t ghcr.io/${{ github.repository_owner }}/drive-app:latest .
          echo ${{ secrets.GHCR_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker push ghcr.io/${{ github.repository_owner }}/drive-app:latest
      - name: Deploy over SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VM_HOST }}
          username: ubuntu
          key: ${{ secrets.VM_SSH_KEY }}
          script: |
            cd /opt/drive-app
            docker compose pull
            docker compose up -d
```

**Outcome:** pushing to `main` rebuilds and redeploys automatically; visiting `https://yourdomain.com` shows the same app that worked locally, now with a valid TLS cert issued automatically by Caddy.

### 6.4 Deployment checklist

- [ ] R2 bucket + API token created, credentials in `.env` (never committed)
- [ ] DB choice made (A or B) and confirmed reachable from the VM
- [ ] Domain DNS A-record points at the VM's public IP
- [ ] `.env` populated: R2 keys, DB credentials, JWT secret, RabbitMQ credentials
- [ ] Security list allows 80/443 only (SSH restricted to your IP)
- [ ] `docker compose up -d` on the VM brings up all services healthy
- [ ] Full click-through test repeated against the live domain (same checks as Step 11, against prod)

---

## 7. Resume/interview framing

*"Designed and built a personal cloud-storage system as a modular monolith with clear domain boundaries (auth, files, metadata, sharing), backed by Cloudflare R2 for egress-free object storage and [Oracle Autonomous DB / self-hosted Postgres] for metadata. Extracted thumbnailing and virus-scanning into a separate service communicating via RabbitMQ with a transactional outbox for reliable event delivery. Instrumented with OpenTelemetry and Resilience4j, deployed via Terraform-provisioned infrastructure and GitHub Actions CI/CD."*

That sentence demonstrates: bounded-context design, informed use (not overuse) of microservices, event reliability patterns, observability, resilience engineering, and IaC — the full senior-backend checklist, backed by something you can actually demo live.

---

## Appendix — local docker-compose.local.yml

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: drive
      POSTGRES_USER: drive
      POSTGRES_PASSWORD: localdev
    ports: ["5432:5432"]
  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]
  jaeger:
    image: jaegertracing/all-in-one:1.57
    ports: ["16686:16686", "4318:4318"]
```
