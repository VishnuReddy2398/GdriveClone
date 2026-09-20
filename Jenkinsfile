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
            }
        }
    }
}