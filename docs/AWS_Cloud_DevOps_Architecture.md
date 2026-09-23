# Scenario 3: Amazon Web Services (AWS) Production Architecture

AWS is the industry leader for Cloud hosting. The architecture is conceptually identical to Azure, but the tool names change. 

## 1. The AWS Infrastructure Components

Instead of VMs or Azure resources, you provision these AWS Managed Services (again, usually using **Terraform**):

1. **Amazon EKS (Elastic Kubernetes Service):** 
   The managed Kubernetes cluster. AWS manages the API servers, and you just tell it how many worker nodes you need (usually managed as an Auto Scaling Group of EC2 instances).
2. **Amazon ECR (Elastic Container Registry):** 
   The AWS version of Docker Hub. Highly secure and scales infinitely.
3. **Amazon RDS for PostgreSQL:** 
   The AWS managed database. You don't have to worry about backups or patching; AWS handles it. You just point your `backend` pods to the RDS endpoint URL.
4. **AWS Secrets Manager:** 
   The AWS equivalent of HashiCorp Vault.

---

## 2. The Cloud-Native CI/CD Workflow

In AWS, you can still use Jenkins (many companies do), but you can also use **AWS CodePipeline** or **GitHub Actions**. Here is the flow using GitHub Actions:

### Step 1: GitHub Actions Trigger
- Developer pushes code to GitHub.
- GitHub Actions starts a runner. 

### Step 2: Build and Push to ECR
- The pipeline authenticates to AWS using **OIDC (OpenID Connect)**. This is a massive security upgrade because it means you do *not* have to store AWS Access Keys in GitHub. It authenticates dynamically!
- The pipeline builds the image: `docker build -t 123456789.dkr.ecr.us-east-1.amazonaws.com/gdrive-backend:v3 .`
- The pipeline pushes the image to ECR.

### Step 3: Deploy to DEV (EKS)
- The pipeline updates the `k8s/backend.yaml` file to use the new `v3` tag.
- It authenticates to the EKS cluster and runs `kubectl apply -n dev`.

### Step 4: Approval and PROD (EKS)
- The pipeline pauses for manual human approval.
- Once approved, the exact same `v3` image is deployed to the production namespace: `kubectl apply -n prod`.

---

## 3. Day 2 Operations in AWS

AWS provides robust tools to monitor the cluster after deployment.

### A. Centralized Logging & Monitoring (CloudWatch)
- **AWS CloudWatch Container Insights:** Similar to Azure, AWS can automatically scrape metrics and logs from your EKS cluster without needing Prometheus or the ELK stack. 
- All logs from your Java backend automatically flow into **CloudWatch Logs**, where you can search them. You can also set up alarms (e.g., "If the word 'ERROR' appears 50 times in 1 minute, page the on-call engineer").

### B. Security & Secrets (AWS Secrets Manager)
- The DevOps engineer saves the PostgreSQL password in **AWS Secrets Manager**.
- Using the **External Secrets Operator** (just like with Vault), EKS connects to AWS Secrets Manager using IAM Roles, fetches the password, and creates the native Kubernetes Secret for your pods to use.

### C. Traffic Management (AWS ALB Ingress Controller)
- On-premise, we used NGINX. In AWS, you use the **AWS Load Balancer Controller**.
- When you create an `ingress.yaml` file in EKS, AWS magically provisions a physical **Application Load Balancer (ALB)** in your AWS account. It automatically hooks up your public domain (`finbudi.com`), attaches SSL/HTTPS certificates from **AWS Certificate Manager**, and routes traffic to your EKS nodes!
