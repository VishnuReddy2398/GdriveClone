# GdriveClone: On-Premises (VM) Production Architecture

This document acts as your comprehensive guide to setting up a true **On-Premises Production Environment** from scratch. We will progress step-by-step so you learn *what* we are doing and *why* we are doing it.

## 🎯 What Are We Doing?
We are going to simulate exactly how a real company hosts its own physical infrastructure (or VMs) rather than paying AWS or Azure. We are starting from scratch on a **Windows Host Machine**.

We will intentionally:
1. Build 3 distinct servers (Virtual Machines).
2. Network them securely so they can talk to each other.
3. Use **Ansible** to automate software installation across multiple machines simultaneously.
4. Set up an Enterprise CI/CD pipeline that automatically pulls from GitHub, scans for security flaws, builds, tests, and deploys.

## 🤔 How Are We Doing It?
We will use **VirtualBox** on your Windows machine to spin up Ubuntu Linux servers.

Your setup will look like this:
```text
Your Windows Machine (Putty & Chrome)
 │
 └── VirtualBox
      │
      ├── DevOps-Server (Runs Jenkins, SonarQube, Vault) -> 4GB RAM
      ├── Dev-Cluster   (Runs K3s Kubernetes Staging)    -> 8GB RAM
      └── Prod-Cluster  (Runs K3s Kubernetes Live)       -> 8GB RAM
```

---

## 🛠️ PHASE 1: VirtualBox VM Creation & Networking

You have VirtualBox installed on your Windows machine and the `ubuntu-server.iso` downloaded. 

**Step 1: Create the 3 Virtual Machines**
Open VirtualBox and click **New**. You will do this 3 times:
1. **Name:** `DevOps-Server` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 4096 MB | **Disk:** 30GB
2. **Name:** `Dev-Cluster` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 8192 MB | **Disk:** 40GB
3. **Name:** `Prod-Cluster` | **Type:** Linux/Ubuntu (64-bit) | **RAM:** 8192 MB | **Disk:** 40GB

**Step 2: Configure VirtualBox Networking (Crucial Step)**
By default, VirtualBox uses "NAT". This means the VMs can access the internet to download packages, but they are invisible to your Windows machine and to each other! We must fix this.

For **each of the 3 VMs**, do the following:
1. Right-click the VM -> **Settings** -> **Network**.
2. **Adapter 1:** Leave it as **NAT**. 
3. **Adapter 2:** Enable Network Adapter. Change "Attached to" to **Host-only Adapter**. 
   *(Why? This assigns a private, static IP address that your Windows machine can securely use to SSH into the VM).*

**Step 3: Install Ubuntu OS**
1. Start the `DevOps-Server` VM. Select your `ubuntu-server.iso`.
2. Follow the Ubuntu installation wizard. 
3. **Important:** When it asks about software to install, check the box for **OpenSSH Server**. (You absolutely need this to connect via Putty later).
4. Finish the installation, reboot, and repeat for the other 2 VMs.

**Step 4: Get the IP Addresses**
Log into each VM using the small VirtualBox window and type:
```bash
ip a
```
Look for `enp0s8` (Adapter 2). Note down the IP address for each VM. For this lab, we assume:
- **DevOps VM:** `192.168.56.10`
- **Dev VM:** `192.168.56.20`
- **Prod VM:** `192.168.56.30`

*(You can now minimize the VirtualBox app entirely. We will never type inside it again!)*

---

## 💻 PHASE 2: Connecting from Windows (Putty & Browsers)

Real DevOps engineers connect to Linux servers remotely using SSH.

**1. Connecting via Putty (SSH)**
1. Download and open **Putty** on your Windows machine.
2. In the "Host Name" box, type `192.168.56.10`.
3. Click **Open**. A black terminal will appear. Type your Ubuntu username and password.
4. Open two more Putty windows and connect to DEV (`192.168.56.20`) and PROD (`192.168.56.30`).

**2. Accessing Web UI Tools from Windows**
Because of the "Host-only Adapter" you configured in Phase 1, your Windows web browser can directly access tools running on the VMs! You will use these later:
- **Jenkins UI:** Chrome -> `http://192.168.56.10:8080`
- **SonarQube UI:** Chrome -> `http://192.168.56.10:9000`
- **Grafana UI:** Chrome -> `http://192.168.56.30:3000`

---

## 🚀 PHASE 3: Infrastructure Setup (Ansible)

Instead of typing commands in all 3 VMs manually, DevOps engineers use **Ansible** to automate software installation.

**Step 1: Install Ansible on the DevOps VM**
Use Putty to connect to the DevOps VM (`192.168.56.10`):
```bash
sudo apt update
sudo apt install -y ansible
```

**Step 2: Set up Passwordless SSH (The Production Way)**
Ansible needs a way to securely connect to the DEV and PROD VMs without prompting you to type a password every single time. We do this using SSH Keys.
On the DevOps VM, run:
```bash
ssh-keygen -t rsa -b 4096 -N ""
ssh-copy-id user@192.168.56.20
ssh-copy-id user@192.168.56.30
```
*(Now the DevOps VM can securely connect to DEV and PROD instantly!)*

**Step 3: The `visudo` Trick**
Ansible also needs to run commands as `root` (like installing packages). 
You must log into VM 2 and VM 3 using Putty, type `sudo visudo`, and add this exact line to the bottom of the file:
```text
user ALL=(ALL) NOPASSWD:ALL
```
*(This tells the Linux OS: "If user runs sudo, do not ask for a password").*

**Step 4: Create the Inventory and Run Ansible**
Create a file named `inventory.ini` on the DevOps VM:
```ini
[kubernetes]
192.168.56.20
192.168.56.30
```

Create an automation playbook named `install_k3s.yaml`:
```yaml
- hosts: kubernetes
  tasks:
    - name: Install K3s (Lightweight Production Kubernetes)
      shell: curl -sfL https://get.k3s.io | sh -
```

**Run Ansible!**
```bash
ansible-playbook -i inventory.ini install_k3s.yaml
```
*(Watch as Ansible connects to both DEV and PROD at the exact same time and installs Kubernetes flawlessly.)*

---

## 🏗️ PHASE 4: Setting up the DevOps Tooling

On the DevOps VM (`192.168.56.10`), we need to install the brain of our operation: the CI/CD pipeline tools.

**1. Install Docker:**
```bash
sudo apt update && sudo apt install -y docker.io
```

**2. Start a Local Docker Registry:**
Why? Because in a real company, source code is Intellectual Property (IP). You never push proprietary images to public Docker Hub. We run a private registry locally.
```bash
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

**3. Start SonarQube:**
SonarQube scans the Java code for vulnerabilities (like hardcoded passwords) and code smells.
```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts
```

**4. Start Jenkins:**
```bash
docker run -d -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock jenkins/jenkins:lts
```

---

## 🔐 PHASE 5: Credential Connections (Jenkins -> Kubernetes)

Jenkins is running on VM 1. How does it get permission to tell VM 2 and VM 3 to update their pods? 

**Step 1: Get the Kubeconfig**
Use Putty to connect to VM 2 (DEV) and print the Kubernetes master password file:
```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```
*(Copy the massive block of text. Do the exact same thing for VM 3 PROD).*

**Step 2: Save them securely in Jenkins**
1. Open Jenkins in your Windows Chrome browser (`http://192.168.56.10:8080`).
2. Go to **Manage Jenkins** -> **Credentials**.
3. Add a new credential of type **Secret File**.
4. Upload the DEV `k3s.yaml` file and name it `dev-kubeconfig`.
5. Upload the PROD `k3s.yaml` file and name it `prod-kubeconfig`.

---

## ⚙️ PHASE 6: The Automated CI/CD Jenkinsfile

Now, Jenkins can execute a true Continuous Delivery pipeline. 

It checks out code -> Scans it in SonarQube -> Builds the Image -> Pushes to the Private Registry -> Deploys to DEV using the `dev-kubeconfig` -> Pauses for Human Approval -> Deploys the *exact same image* to PROD using the `prod-kubeconfig`.

*(Your `Jenkinsfile` in the repository is already perfectly configured to do this!)*

---

## 🌐 PHASE 7: Namecheap Domain Configuration (finbudi.com)

You own `finbudi.com` on Namecheap. You want real users on the internet to hit your PROD cluster (VM 3).

**Step 1: Get your Home's Public IP**
Google "What is my IP" on your Windows machine. Let's assume it is `203.0.113.50`.

**Step 2: Port-Forward your Home Router**
You must log into your physical home WiFi router settings and forward Port 80 and Port 443 to VM 3's IP (`192.168.56.30`).

**Step 3: Namecheap DNS Configuration**
1. Log into Namecheap and go to the **Advanced DNS** tab for `finbudi.com`.
2. Delete any existing parking records.
3. Add an **A Record**:
   - Host: `@`
   - Value: `203.0.113.50` (Your home public IP)
   - TTL: Automatic
4. Add a **CNAME Record**:
   - Host: `www`
   - Value: `finbudi.com`

**Step 4: The Kubernetes Ingress**
In your `k8s/ingress.yaml` file on the PROD cluster, tell the NGINX controller to listen for the domain:
```yaml
spec:
  rules:
  - host: finbudi.com
```
*(Now, anyone in the world typing finbudi.com will hit your home router -> VM 3 -> NGINX -> Your React frontend!)*

---

## 🛡️ PHASE 8: Day 2 Operations (PLG Stack & Vault)

A DevOps engineer's job isn't done when the app is deployed. They have to maintain the system.

### A. Monitoring Metrics (Prometheus & Grafana)
We need to monitor if the pods crash or run out of memory. 
1. Use Putty to SSH into VM 3 (PROD).
2. Install the Prometheus/Grafana stack using Helm:
   ```bash
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm install monitoring prometheus-community/kube-prometheus-stack
   ```
3. Open Grafana in your Windows browser (`http://192.168.56.30:3000`). It comes with pre-built dashboards showing CPU and RAM for every pod. If a pod crashes 3 times, Prometheus sends an alert to PagerDuty to wake up the DevOps engineer!

### B. Centralized Logging (Loki & Promtail)
If a user gets an error in Production, you do not log into the VM and type `kubectl logs`. You install **Promtail** (which sucks up logs) and **Loki** (which stores them).
1. On VM 3, run:
   ```bash
   helm repo add grafana https://grafana.github.io/helm-charts
   helm install loki grafana/loki-stack
   ```
2. Now, in the same Grafana dashboard, you can search all logs across the entire cluster instantly.

### C. Secret Management (HashiCorp Vault)
You run Vault on VM 1. You log into the Vault UI (`http://192.168.56.10:8200`) to save passwords. The `External Secrets Operator` running on VM 2 and VM 3 automatically fetches them in real-time. This replaces Kubernetes standard Secrets so developers never see the raw passwords.
