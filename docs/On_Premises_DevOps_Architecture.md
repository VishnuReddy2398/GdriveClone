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
3. **Important:** When it asks about software to install, check the box for **OpenSSH Server**. (You need this to use Putty later).
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

---

## 💻 PHASE 2: Connecting from Windows (Putty & Browsers)

You should never type inside the small VirtualBox window. Real DevOps engineers minimize VirtualBox and connect remotely.

### 1. Connecting via Putty (SSH)
1. Download and open **Putty** on your Windows machine.
2. In the "Host Name (or IP address)" box, type `192.168.56.10`.
3. Click **Open**. A black terminal will appear. Type your Ubuntu username and password.
4. Repeat this to open separate Putty windows for DEV (`192.168.56.20`) and PROD (`192.168.56.30`).

### 2. Accessing Web UI Tools from Windows
Because of the "Host-only Adapter", your Windows web browser can directly access tools running on the VMs:
- **Jenkins UI:** Open Chrome and go to `http://192.168.56.10:8080`
- **SonarQube UI:** Open Chrome and go to `http://192.168.56.10:9000`
- **Grafana UI:** Open Chrome and go to `http://192.168.56.30:3000`

---

## 🚀 PHASE 3: Infrastructure Setup (Ansible)

Instead of typing commands in all 3 VMs manually, DevOps engineers use **Ansible** to automate the installation.

### Step 1: Install Ansible on the DevOps VM
Use Putty to connect to the DevOps VM (`192.168.56.10`):
```bash
sudo apt update
sudo apt install -y ansible sshpass
```

### Step 2: Set up Passwordless SSH & Sudo (The Production Way)
Ansible needs a way to securely connect to the other VMs without prompting for a password. 
On the DevOps VM, run:
```bash
ssh-keygen -t rsa -b 4096 -N ""
ssh-copy-id user@192.168.56.20
ssh-copy-id user@192.168.56.30
```
**Important `visudo` Step:**
Ansible also needs to run commands as `root`. You must log into VM 2 and VM 3 using Putty, type `sudo visudo`, and add this line to the bottom of the file:
```text
user ALL=(ALL) NOPASSWD:ALL
```

### Step 3: Create the Inventory and Run Ansible
Create a file named `inventory.ini`:
```ini
[kubernetes]
192.168.56.20
192.168.56.30
```

Create a playbook named `install_k3s.yaml`:
```yaml
- hosts: kubernetes
  tasks:
    - name: Install K3s (Lightweight Production Kubernetes)
      shell: curl -sfL https://get.k3s.io | sh -
```

Run Ansible:
```bash
ansible-playbook -i inventory.ini install_k3s.yaml
```

---

## 🏗️ PHASE 4: Setting up the DevOps Tooling

On the DevOps VM (`192.168.56.10`), we install our CI/CD pipeline tools.

**1. Install Docker:**
```bash
sudo apt update && sudo apt install -y docker.io
```

**2. Start a Local Docker Registry:**
We run a private registry locally so our code isn't exposed to the public.
```bash
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

**3. Start SonarQube:**
```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts
```

**4. Start Jenkins:**
```bash
docker run -d -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock jenkins/jenkins:lts
```

---

## 🔐 PHASE 5: Credential Connections (Jenkins -> Kubernetes)

Jenkins is running on VM 1. How does it get permission to deploy code to VM 2 and VM 3?

**Step 1: Get the Kubeconfig**
Use Putty to connect to VM 2 (DEV) and print the master password file:
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

## ⚙️ PHASE 6: The Automated CI/CD Jenkinsfile

*(The Jenkinsfile remains exactly as written in the repository: 1 Pipeline that builds once, deploys to DEV, waits for approval, and deploys the same image to PROD).*

---

## 🌐 PHASE 7: Namecheap Domain Configuration (finbudi.com)

If you own `finbudi.com` on Namecheap, you want real users on the internet to hit your PROD cluster. 
*(Note: This requires you to port-forward your physical home router to VM 3. Look up "How to port forward port 80/443 on my router").*

**Step 1: Get your Home's Public IP**
Google "What is my IP". Let's assume it is `203.0.113.50`.

**Step 2: Namecheap DNS Configuration**
1. Log into Namecheap and go to the **Advanced DNS** tab for `finbudi.com`.
2. Delete any existing parking records.
3. Add an **A Record**:
   - Host: `@`
   - Value: `203.0.113.50` (Your home public IP)
   - TTL: Automatic
4. Add a **CNAME Record**:
   - Host: `www`
   - Value: `finbudi.com`

**Step 3: The Kubernetes Ingress**
In your `k8s/ingress.yaml` file on the PROD cluster, the host must match:
```yaml
spec:
  rules:
  - host: finbudi.com
```

---

## 🛡️ PHASE 8: Day 2 Operations (PLG Stack & Vault)

We do not use ELK; the modern, lightweight Kubernetes standard is the **PLG Stack** (Prometheus, Loki, Grafana).

### A. Monitoring Metrics (Prometheus & Grafana)
We need to monitor if the pods crash or run out of memory. 
1. Use Putty to SSH into VM 3 (PROD).
2. Install the Prometheus/Grafana stack using Helm:
   ```bash
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm install monitoring prometheus-community/kube-prometheus-stack
   ```
3. Open Grafana in your Windows browser (`http://192.168.56.30:3000`). It comes with pre-built dashboards showing CPU and RAM for every pod.

### B. Centralized Logging (Loki & Promtail)
Instead of ELK, we install **Promtail** (which sucks up logs) and **Loki** (which stores them).
1. On VM 3, run:
   ```bash
   helm repo add grafana https://grafana.github.io/helm-charts
   helm install loki grafana/loki-stack
   ```
2. Now, in the same Grafana dashboard you used for Prometheus, you can add Loki as a data source and search all logs across the entire cluster instantly.

### C. Secret Management (HashiCorp Vault)
You run Vault on VM 1. You log into the Vault UI (`http://192.168.56.10:8200`) to save passwords. The `External Secrets Operator` running on VM 2 and VM 3 automatically fetches them in real-time. This replaces standard Secrets so developers never see raw passwords.
