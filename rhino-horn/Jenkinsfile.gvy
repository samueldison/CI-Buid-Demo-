Object gv
final String DEFAULT_PORT = '8084'
final String STAGING = 'staging'
final String DEV = 'dev'

Boolean trivyPassed() {
    return (env.TRIVY_SCAN_STATUS ?: '-1').toInteger() == 0
}

pipeline {

    agent any

    tools {
        maven 'maven-3.9.9'
    }

    parameters {
        string(name: 'BUILD_NUM_TO_KEEP', defaultValue: '2', description: 'Number of builds to retain.')
        string(name: 'BUILD_DAYS_TO_KEEP', defaultValue: '7', description: 'Discard builds older than specified days.')
        string(name: 'BUILD_ARTIFACT_NUM_TO_KEEP', defaultValue: '2', description: 'Number of artifacts to retain')
        string(name: 'BUILD_ARTIFACT_DAYS_TO_KEEP', defaultValue: '2',
            description: 'Discard artifacts older than specified days.')
        string(name: 'CONTAINER_PORT', defaultValue: DEFAULT_PORT, description: 'Port to expose within the container.')
        choice(name: 'BRANCH_NAME', choices: ['main', DEV, STAGING], description: 'Git branch to build.')
        choice(name: 'MAIL_TO', choices: ['user1', 'user2'], description: 'Email recipient ID')
        choice(name: 'PROJECT_VERSION', choices: ['1.0', '1.1', '1.2'], description: 'Version of the project to build.')
        choice(name: 'ENVIRONMENT', choices: [DEV, STAGING], description: 'Environment to deploy to.')
        choice(name: 'TRIVY_SEVERITY', choices: ['HIGH', 'CRITICAL'], description: 'Severity levels for scan.')
        string(name: 'HOST_PORT', defaultValue: DEFAULT_PORT, description: 'Host port for smoke testing.')
        booleanParam(name: 'FAIL_ON_LEAKS', defaultValue: true, description: 'Fail the build if secrets are found.')
    }

    options {
        timestamps()
    }

    environment {
        GIT_URL           = 'https://github.com/Orion83-h/Safari.git'
        GIT_CREDS         = 'gitcreds'
        DOCKERFILE        = 'rhino-horn/Dockerfile.v1'
        DOCKER_NAMESPACE  = 'colanta06'
        MAJOR_VERSION     = '1'
        MINOR_VERSION     = '0'
        PATCH_VERSION     = "${env.BUILD_NUMBER}"
        IMAGE_TAG         = "v${MAJOR_VERSION}.${MINOR_VERSION}.${env.PATCH_VERSION}"
        IMAGE_NAME        = "${DOCKER_NAMESPACE}/${env.JOB_NAME.replaceAll('/', '-').toLowerCase()}:${env.IMAGE_TAG}"
        CONTAINER_NAME    = "rhino-horn-${params.ENVIRONMENT}"
        SONAR_ORG         = 'safari'
        SONAR_PROJECT_KEY = 'safari_rhino-horn'
        TRIVY_CACHE_DIR   = '/tmp/trivy'
        TRIVY_SCAN_STATUS = '-1'
        S3_BUCKET_NAME    = "trivy-reports-${params.ENVIRONMENT}"
    }

    stages {
        stage('init') {
            steps {
                script {
                    properties([
                        buildDiscarder(logRotator(
                            numToKeepStr:          params.BUILD_NUM_TO_KEEP,
                            daysToKeepStr:         params.BUILD_DAYS_TO_KEEP,
                            artifactNumToKeepStr:  params.BUILD_ARTIFACT_NUM_TO_KEEP,
                            artifactDaysToKeepStr: params.BUILD_ARTIFACT_DAYS_TO_KEEP
                        ))
                    ])
                    gv = load 'rhino-horn/script.groovy'
                }
            }
        }

        stage('Pull Src Code') {
            steps {
               script {
                   gv.pullSourceCode()
               }
            }
        }

        stage('Secrets Scan (Gitleaks)') {
            steps {
                script {
                    gv.secretsScan(params.FAIL_ON_LEAKS)
                }
            }
        }

        stage('Build Maven Project') {
            steps {
                script {
                    gv.buildMavenProject()
                }
            }
        }

        stage('Deploy To Nexus') {
            steps {
                script {
                    gv.deployToNexus()
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    gv.sonarCloudAnalysis()
                }
            }
        }

        stage('Check Dockerfile') {
            steps {
                script {
                    gv.checkDockerfile()
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    if (fileExists(env.DOCKERFILE)) {
                        gv.buildDockerImage()
                    }
                }
            }
        }

        stage('Trivy Scan') {
            steps {
                script {
                    env.TRIVY_SCAN_STATUS = gv.trivyScan()
                }
            }
            post {
                failure {
                    error("Trivy scan failed with an exception — downstream push and smoke test are blocked.")
                }
            }
        }

        stage('Upload Trivy Report to S3') {
            steps {
                script {
                    gv.uploadTrivyReportToS3()
                }
            }
        }

        stage('Push Docker Image') {
            when { expression { trivyPassed() } }
            steps {
                script {
                    gv.pushDockerImage()
                }
            }
        }

        stage('Smoke Test') {
            when { expression { trivyPassed() } }
            steps {
                script {
                    gv.smokeTest()
                }
            }
        }

        stage('Cleanup') {
            
            steps {
                script {
                    gv.cleanup()
                }
            }
        }
    }

    post {
        success {
            script {
                String reportUrl = "${env.BUILD_URL}artifact/trivy-reports/trivy-report.html"

                build job: 'helm-chart-update',
                    parameters: [
                        string(name: 'IMAGE_NAME', value: "${env.IMAGE_NAME}"),
                        string(name: 'IMAGE_TAG', value: "${env.IMAGE_TAG}"),
                        string(name: 'ENVIRONMENT', value: "${params.ENVIRONMENT}")
                    ]

                emailext(
                    to: gv.getEmailForUsers(params.MAIL_TO),
                    subject: "SUCCESS: ${env.JOB_NAME} - Build ${env.BUILD_NUMBER}",
                    body: """
                        <h2>Build Succeeded</h2>
                        <p>Job: ${env.JOB_NAME}</p>
                        <p>Build Number: ${env.BUILD_NUMBER}</p>
                        <p>Environment: ${params.ENVIRONMENT}</p>
                        <p>Image: ${env.IMAGE_NAME}</p>
                        <p>Trivy Scan: ${env.TRIVY_SCAN_STATUS == '0' ? 'No vulnerabilities' :
                        env.TRIVY_SCAN_STATUS == '1' ? 'Vulnerabilities found' :
                        env.TRIVY_SCAN_STATUS == '-1' ? 'Scan not run' : 'Scan failed'}</p>
                        <p><a href="${reportUrl}">View Trivy Report</a></p>
                        <p><a href="${env.BUILD_URL}">View Build Details</a></p>
                    """,
                    mimeType: 'text/html',
                    attachmentsPattern: 'trivy-reports/**'
                )
            }
        }

        failure {
            script {
                String reportUrl = "${env.BUILD_URL}artifact/trivy-reports/trivy-report.html"
                emailext(
                    to: gv.getEmailForUsers(params.MAIL_TO),
                    subject: "FAILED: ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}",
                    body: """
                        <h2>Build Failed</h2>
                        <p>Job: ${env.JOB_NAME}</p>
                        <p>Build Number: ${env.BUILD_NUMBER}</p>
                        <p>Environment: ${params.ENVIRONMENT}</p>
                        <p>Failed Stage: ${currentBuild.result}</p>
                        <p>Trivy Scan: ${env.TRIVY_SCAN_STATUS == '0' ? 'No vulnerabilities' :
                        env.TRIVY_SCAN_STATUS == '1' ? 'Vulnerabilities found' :
                        env.TRIVY_SCAN_STATUS == '-1' ? 'Scan not run' : 'Scan failed'}</p>
                        <p><a href="${reportUrl}">View Trivy Report</a></p>
                        <p><a href="${env.BUILD_URL}">View Build Details</a></p>
                    """,
                    mimeType: 'text/html',
                    attachmentsPattern: 'trivy-reports/**'
                )
            }
        }

        always {
            cleanWs(
                cleanWhenNotBuilt: false,
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
            )
        }
    }
}