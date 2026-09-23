# Scenario 1: On-Premises (VirtualBox VMs) Production Architecture - Full Setup Guide

This scenario covers exactly how a company hosts its own physical infrastructure (or VMs). We are starting from scratch on a **Windows Host Machine** using VirtualBox and Ubuntu Server.

---

## 🛠️ PHASE 1: VirtualBox VM Creation & Networking

You have VirtualBox installed on your Windows machine and the `ubuntu-server.iso` downloaded. Here is the exact setup.

### Step 1: Create the 3 Virtual Machines
Open VirtualBox and click **New**. You will do this 3 times to create 3 separate VMs.
1. **Name:** `DevOps-Server` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 4096 MB (4GB) | **Disk:** 30GB
2. **Name:** `Dev-Cluster` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 8192 MB (8GB) | **Disk:** 40GB
3. **Name:** `Prod-Cluster` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 8192 MB (8GB) | **Disk:** 40GB

### Step 2: Configure VirtualBox Networking (Crucial Step)
By default, VirtualBox uses "NAT", meaning the VMs can access the internet, but they cannot talk to each other, and you cannot SSH into them from your Windows terminal. We must fix this.

For **each of the 3 VMs**, do the following:
1. Right-click the VM -> **Settings** -> **Network**.
2. **Adapter 1:** Leave it as **NAT** (This gives the VM internet access to download packages).
3. **Adapter 2:** Enable Network Adapter. Change "Attached to" to **Host-only Adapter**. 
   *(This gives the VM a static IP address that your Windows machine and the other VMs can securely talk to).*

### Step 3: Install Ubuntu OS
1. Start the `DevOps-Server` VM. It will ask for an ISO file. Select your `ubuntu-server.iso`.
2. Follow the Ubuntu installation wizard. 
3. **Important:** When it asks about software to install, check the box for **OpenSSH Server**. (You need this to use terminal/Putty later).
4. Finish the installation, reboot, and repeat for the other 2 VMs.

### Step 4: Get the IP Addresses
Log into each VM using the VirtualBox window and type:
```bash
ip a
```
Look for `enp0s8` (Adapter 2). Note down the IP address for each VM.
- **DevOps VM:** `192.168.56.10`
- **Dev VM:** `192.168.56.20`
- **Prod VM:** `192.168.56.30`

*(You can now minimize VirtualBox entirely. Open Windows PowerShell or Git Bash and use `ssh username@192.168.56.10` to control them!)*

---

## 🚀 PHASE 2: Infrastructure Setup (Ansible)

Instead of typing commands in all 3 VMs manually, DevOps engineers use **Ansible** to automate the installation.

### Step 1: Install Ansible on the DevOps VM
SSH into the DevOps VM (`192.168.56.10`) from your Windows machine:
```bash
sudo apt update
sudo apt install -y ansible sshpass
```

### Step 2: Create the Inventory
Create a file named `inventory.ini`:
```ini
[kubernetes]
192.168.56.20
192.168.56.30
```

### Step 3: Write the Kubernetes Automation Playbook
Create a file named `install_k3s.yaml` to automatically install Kubernetes on the DEV and PROD servers:
```yaml
- hosts: kubernetes
  tasks:
    - name: Install K3s (Lightweight Production Kubernetes)
      shell: curl -sfL https://get.k3s.io | sh -
```

### Step 4: Run Ansible!
```bash
ansible-playbook -i inventory.ini install_k3s.yaml -k
```
*(Ansible instantly connects via SSH and installs Kubernetes on both VMs at the exact same time!)*

---

## 🏗️ PHASE 3: Setting up the DevOps Tooling

On the DevOps VM (`192.168.56.10`), we need to install our CI/CD pipeline tools.

**1. Install Docker:**
```bash
sudo apt update && sudo apt install -y docker.io
```

**2. Start a Local Docker Registry:**
We don't use Docker Hub. We run a private registry locally.
```bash
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

**3. Start SonarQube:**
SonarQube scans the Java code for vulnerabilities.
```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts
```

**4. Start Jenkins:**
```bash
docker run -d -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock jenkins/jenkins:lts
```

---

## 🔐 PHASE 4: Credential Connections (Jenkins -> Kubernetes)

Jenkins is running on VM 1. How does it get permission to deploy code to VM 2 and VM 3?

**Step 1: Get the Kubeconfig**
SSH into VM 2 (DEV) and print the master password file:
```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```
*(Copy the output. Do the same for VM 3 PROD).*

**Step 2: Save them in Jenkins**
1. Open Jenkins in your Windows browser (`http://192.168.56.10:8080`).
2. Go to **Manage Jenkins** -> **Credentials**.
3. Add a new credential of type **Secret File**.
4. Upload the DEV `k3s.yaml` file and name it `dev-kubeconfig`.
5. Upload the PROD `k3s.yaml` file and name it `prod-kubeconfig`.

---

## ⚙️ PHASE 5: The Automated CI/CD Jenkinsfile

Here is the exact `Jenkinsfile` that builds the code, runs SonarQube, deploys to DEV, and waits for manual approval to deploy to PROD.

```groovy
pipeline {
    agent any
    environment {
        // Points to the local registry on VM 1
        REGISTRY = "192.168.56.10:5000"
        IMAGE_NAME = "${REGISTRY}/gdrive-backend"
        IMAGE_TAG = "${GIT_COMMIT.take(7)}" 
    }
    stages {
        stage('Checkout & Unit Tests') {
            steps {
                checkout scm
                sh './mvnw clean test'
            }
        }

        stage('SonarQube Security Scan') {
            steps {
                // Scans the code and sends results to VM 1 port 9000
                withSonarQubeEnv('SonarQube-Server') {
                    sh './mvnw sonar:sonar -Dsonar.projectKey=gdriveclone'
                }
            }
        }

        stage('Build & Push to Local Registry') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Deploy to DEV (VM 2)') {
            steps {
                // Fetch the secure DEV kubeconfig from Jenkins Credentials
                withCredentials([file(credentialsId: 'dev-kubeconfig', variable: 'KUBECONFIG')]) {
                    sh "kubectl --kubeconfig=$KUBECONFIG set image deployment/backend backend=${IMAGE_NAME}:${IMAGE_TAG}"
                    sh "kubectl --kubeconfig=$KUBECONFIG rollout status deployment/backend"
                }
            }
        }

        stage('Production Approval') {
            input {
                message 'DEV is stable. Deploy to PRODUCTION Cluster (VM 3)?'
                ok 'Approve'
            }
        }

        stage('Deploy to PROD (VM 3)') {
            steps {
                withCredentials([file(credentialsId: 'prod-kubeconfig', variable: 'KUBECONFIG')]) {
                    // Deploy the EXACT SAME IMAGE to production. Never rebuild!
                    sh "kubectl --kubeconfig=$KUBECONFIG set image deployment/backend backend=${IMAGE_NAME}:${IMAGE_TAG}"
                    sh "kubectl --kubeconfig=$KUBECONFIG rollout status deployment/backend"
                }
            }
        }
    }
}
```

---

## 🛡️ PHASE 6: Day 2 Operations

### A. Centralized Logging (ELK Stack)
If a user gets an error in Production, you do not log into the VM. You install **Fluentd** on VM 3. It automatically sucks up every log from every pod and sends them to Elasticsearch. You use Kibana in your Windows browser to search the logs.

### B. Secret Management (HashiCorp Vault)
You run Vault on VM 1. You log into the Vault UI (`http://192.168.56.10:8200`) to save passwords. The `External Secrets Operator` running on VM 2 and VM 3 automatically fetches them in real-time.
