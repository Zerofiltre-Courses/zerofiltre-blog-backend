pipeline {
    agent {
        dockerfile {
            filename 'Dockerfile.build'
            args '-v /root/.m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock'
        }
    }
    stages {
        stage('Test') {
            steps {
                sh 'mvn clean test'
            }

            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv('SonarqubeServer') {
                    sh 'mvn sonar:sonar -s .m2/settings.xml'
                }
            }
        }
        // stage('Quality Gate') {
        //     steps {
        //         timeout(time: 10, unit: 'SECONDS') {
        //             waitForQualityGate abortPipeline: false
        //         }
        //     }
        // }
        stage('Package') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        
        stage('Build and push API to docker registry') {
            steps {
                 withCredentials([usernamePassword(credentialsId: 'DockerHubCredentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh """
                            docker build -t imzerofiltre/zerofiltretech-blog:p5_jenkins_${env.BUILD_NUMBER} .
                            echo "Image build complete"
                            docker login -u $USERNAME -p $PASSWORD
                            docker push imzerofiltre/zerofiltretech-blog:p5_jenkins_${env.BUILD_NUMBER}
                            docker image rm imzerofiltre/zerofiltretech-blog:p5_jenkins_${env.BUILD_NUMBER}
                            echo "Image push complete"
                        """
                    }
            }
                   
        }
    }
    post {
        success {
            script {
                emailext(
                    subject: "Build Successful: ${currentBuild.fullDisplayName}",
                    body: "The build has completed successfully. Check the artifacts here: ${env.BUILD_URL}",
                    from: "philippe.guemkamsimo@gmail.com",
                    to: sh(script: 'git log -1 --format="%ae" \$GIT_COMMIT', returnStdout: true).trim(),
                )
            }
        }
        failure {
            script {
                emailext(
                    subject: "Build Failure: ${currentBuild.fullDisplayName}",
                    body: "The build failed. Check the artifacts here: ${env.BUILD_URL}",
                    from: "philippe.guemkamsimo@gmail.com",
                    to: sh(script: 'git log -1 --format="%ae" \$GIT_COMMIT', returnStdout: true).trim()
                )
            }
        }
    }

}