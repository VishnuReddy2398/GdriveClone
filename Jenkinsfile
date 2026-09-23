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

        stage('Push Images to Docker Hub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKER_USERNAME',
                    passwordVariable: 'DOCKER_PASSWORD'
                )]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

                        docker tag gdriveclone-backend:1.0.0 hashhari/gdriveclone-backend:1.0.0
                        docker tag gdriveclone-frontend:1.0.0 hashhari/gdriveclone-frontend:1.0.0

                        docker push hashhari/gdriveclone-backend:1.0.0
                        docker push hashhari/gdriveclone-frontend:1.0.0

                        docker logout
                    '''
                }
            }
        }
    }
}