pipeline {
    agent any

    stages {

        stage('Build') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw clean test'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t gdriveclone-backend:1.0.0 .'
                sh 'docker build -t gdriveclone-frontend:1.0.0 ./frontend'
            }
        }

        stage('Load Images to Kubernetes') {
            steps {
                // Load the images directly from Mac's Docker into the kind cluster
                sh 'kind load docker-image gdriveclone-backend:1.0.0 --name gdrive-cluster'
                sh 'kind load docker-image gdriveclone-frontend:1.0.0 --name gdrive-cluster'
            }
        }
        
        stage('Deploy to Kubernetes') {
            steps {
                // Apply the latest yaml manifests directly to the cluster
                sh 'cat k8s/backend.yaml | docker exec -i gdrive-cluster-control-plane kubectl apply -f -'
                sh 'cat k8s/frontend.yaml | docker exec -i gdrive-cluster-control-plane kubectl apply -f -'
                
                // Force Kubernetes to pull the new images and restart the applications
                sh 'docker exec -i gdrive-cluster-control-plane kubectl rollout restart deployment backend'
                sh 'docker exec -i gdrive-cluster-control-plane kubectl rollout restart deployment frontend'
            }
        }
    }
}