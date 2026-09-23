# Scenario 2: Microsoft Azure Cloud Production Architecture

When you move from local VMs to the Cloud (Azure), you stop managing the underlying servers. You rent "Managed Services." This drastically reduces the work a DevOps engineer has to do to keep the system alive.

## 1. The Azure Infrastructure Components

Instead of spinning up Ubuntu VMs and installing software manually, you provision these Azure resources (usually using a tool called **Terraform**):

1. **Azure Kubernetes Service (AKS):** 
   Azure manages the control-plane for you. You just tell Azure "Give me a 3-node cluster for Dev, and a 5-node cluster for Prod."
2. **Azure Container Registry (ACR):** 
   Replaces your local VM Docker Registry. This is a highly secure, private Docker Hub hosted by Microsoft.
3. **Azure Database for PostgreSQL:** 
   You do **not** run databases inside AKS in production. You rent a managed database from Azure. Azure handles the automated backups, patching, and scaling.
4. **Azure Key Vault:** 
   Replaces HashiCorp Vault. Microsoft's native secret manager.

---

## 2. The Cloud-Native CI/CD Workflow

In Azure, many companies ditch Jenkins in favor of **Azure DevOps Pipelines** or **GitHub Actions**, because they integrate directly into the cloud.

### Step 1: GitHub Actions / Azure DevOps Trigger
- A developer pushes code to the `main` branch on GitHub.
- GitHub Actions automatically detects the push and starts the pipeline runner (a temporary server that lives just for the duration of the build).

### Step 2: Build and Push to ACR
- The pipeline checks out the code and builds the Docker image.
- Instead of using a password, the pipeline uses **Azure Managed Identities** (secure, passwordless authentication) to securely log into the **Azure Container Registry (ACR)**.
- The image is pushed to ACR.

### Step 3: Deploy to DEV (AKS)
- In Azure, it is common to use the **same AKS Cluster** for Dev and Prod, but isolate them using **Kubernetes Namespaces** (e.g., `dev-namespace` and `prod-namespace`). This saves thousands of dollars.
- The pipeline runs `kubectl apply --namespace=dev` and pulls the image from ACR.

### Step 4: Approval and PROD (AKS)
- The pipeline pauses. A manager clicks approve in the Azure DevOps or GitHub Actions UI.
- The pipeline runs `kubectl apply --namespace=prod`.

---

## 3. Day 2 Operations in Azure

Because you are paying Microsoft, they provide incredible built-in tools for Day 2 operations.

### A. Centralized Logging & Monitoring (Azure Monitor & Log Analytics)
- **You don't need the ELK stack or Prometheus!** 
- AKS has a feature called **Container Insights**. You click a checkbox in the Azure Portal, and Azure automatically collects all CPU metrics and logs from every pod in your cluster.
- You can query logs using **KQL** (Kusto Query Language) directly in the Azure Portal.

### B. Security & Secrets (Azure Key Vault)
- The DevOps engineer stores the PostgreSQL password inside Azure Key Vault.
- Using the **Azure Key Vault Provider for Secrets Store CSI Driver** (a Kubernetes plugin), your pods can mount passwords from Key Vault directly as files or environment variables. No passwords are ever seen in the CI/CD pipeline!

### C. Traffic Management (Azure Application Gateway)
- Instead of installing an NGINX Ingress Controller manually, you use the **Application Gateway Ingress Controller (AGIC)**. 
- When you create an `ingress.yaml`, Azure automatically creates a physical, enterprise-grade Load Balancer with Web Application Firewall (WAF) protection to block hackers from hitting your cluster!
