pipeline {
    agent any
    environment {
        // Points to our Private Registry (In an Enterprise, this would be ACR, ECR, or a local registry)
        REGISTRY = "192.168.56.10:5000"
        IMAGE_NAME = "${REGISTRY}/gdrive-backend"
        // Use the Git Commit Hash for traceability instead of 'latest'
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
                // Jenkins uses the SonarQube Scanner plugin to scan for vulnerabilities
                withSonarQubeEnv('SonarQube-Server') {
                    sh './mvnw sonar:sonar -Dsonar.projectKey=gdriveclone'
                }
            }
        }

        stage('Build & Push to Registry') {
            steps {
                // Build the image locally
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                // Push it to the private registry so other servers can pull it
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Deploy to DEV Cluster') {
            steps {
                // Fetch the secure kubeconfig file from Jenkins Credentials
                withCredentials([file(credentialsId: 'dev-kubeconfig', variable: 'KUBECONFIG')]) {
                    // Inject the new image tag into the deployment
                    sh "kubectl --kubeconfig=$KUBECONFIG set image deployment/backend backend=${IMAGE_NAME}:${IMAGE_TAG} --namespace=default"
                    
                    // Wait for Kubernetes to successfully complete the rolling update
                    sh "kubectl --kubeconfig=$KUBECONFIG rollout status deployment/backend"
                }
            }
        }

        stage('Production Approval') {
            input {
                message 'DEV tests passed. Do you want to deploy to PRODUCTION?'
                ok 'Approve Deployment'
            }
        }

        stage('Deploy to PROD Cluster') {
            steps {
                // Use a completely different set of credentials to access the Prod Cluster
                withCredentials([file(credentialsId: 'prod-kubeconfig', variable: 'KUBECONFIG')]) {
                    // Deploy the exact same image to prod. We never rebuild the image!
                    sh "kubectl --kubeconfig=$KUBECONFIG set image deployment/backend backend=${IMAGE_NAME}:${IMAGE_TAG} --namespace=production"
                    
                    sh "kubectl --kubeconfig=$KUBECONFIG rollout status deployment/backend --namespace=production"
                }
            }
        }
    }
    
    post {
        success {
            echo "Pipeline succeeded! Send notification to Slack..."
        }
        failure {
            echo "Pipeline failed! Paging DevOps on-call..."
        }
    }
}