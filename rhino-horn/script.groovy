def pullSourceCode() {
    echo "====++++Pulling Source Code From Repo++++===="
    checkout([$class: 'GitSCM',
        branches: [[name: "*/${params.BRANCH_NAME}"]],
        doGenerateSubmoduleConfigurations: false,
        extensions: [
            [$class: 'PruneStaleBranch'],
            [$class: 'CleanBeforeCheckout'],
            [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true]
        ],
        userRemoteConfigs: [[credentialsId: env.GIT_CREDS, url: env.GIT_URL]]
    ])
}

def secretsScan(boolean failOnLeaks) {
    echo "====++++Running Gitleaks for Secrets Scanning++++===="
    sh "mkdir -p gitleaks-reports"

    def result = sh(
        script: """
            gitleaks detect --source rhino-horn \
                --report-format sarif \
                --report-path gitleaks-reports/gitleaks-report.sarif \
                --exit-code 1
        """,
        returnStatus: true
    )

    archiveArtifacts artifacts: 'gitleaks-reports/**', fingerprint: true

    if (result != 0) {
        if (failOnLeaks) {
            error("Secrets detected by Gitleaks! Failing the build as FAIL_ON_LEAKS=true.")
        } else {
            unstable("Secrets detected by Gitleaks. Build marked unstable as FAIL_ON_LEAKS=false.")
        }
    } else {
        echo "No secrets detected by Gitleaks."
    }
}

def buildMavenProject() {
    echo "====++++Building Maven Project++++===="
    dir('rhino-horn') {
       sh 'mvn -B clean package -DskipTests'
       sh 'ls -lrt target/'
    }
}

def deployToNexus() {
    echo "==== Deploying to Nexus ===="

    dir('rhino-horn') {

        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-credentials',
                usernameVariable: 'MAVEN_USERNAME',
                passwordVariable: 'MAVEN_PASSWORD'
            )
        ]) {

            withMaven(
                maven: 'maven-3.9.9',
                mavenSettingsConfig: 'maven-settings',
                traceability: true
            ) {
                sh 'mvn clean deploy'
            }
        }
    }
}


def sonarqubeAnalysis() {
    echo "==== SonarQube Code Quality Analysis ===="

    dir('rhino-horn') {
        withSonarQubeEnv('SonarQube') {

            withMaven(
                maven: 'maven-3.9.9',
                mavenSettingsConfig: 'maven-settings'
            ) {
                try {
                    sh '''
                        mvn verify \
                          org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                          -Dsonar.SONAR_PROJECT_NAME=safari_rhino-horn \
                          -Dsonar.projectName=safari_rhino-horn
                    '''
                } catch (Exception e) {
                    error("SonarQube analysis failed: ${e.getMessage()}")
                }
            }
        }
    }
}

def checkDockerfile() {
    echo "====++++Checking Dockerfile++++===="
    if (!fileExists(env.DOCKERFILE)) {
        error("Dockerfile does not exist at path: ${env.DOCKERFILE}")
    }
    echo "Dockerfile exists at path: ${env.DOCKERFILE}"
    def dockerfileContent = readFile(env.DOCKERFILE)
    if (dockerfileContent.contains('FROM scratch')) {
        error("Dockerfile contains 'FROM scratch'. Please use a valid base image.")
    }
    echo "Dockerfile is valid and does not contain 'FROM scratch'."
}

def buildDockerImage() {
    echo "====++++Building Docker Image++++===="
    sh """
        DOCKER_BUILDKIT=1 docker build \
            --no-cache \
            --pull \
            -t ${env.IMAGE_NAME} \
            -f ${env.DOCKERFILE} .
    """
}

def trivyScan() {
    echo "====++++Running Trivy Scan++++===="
    def reportDir = "trivy-reports"

    sh "mkdir -p ${reportDir} ${env.TRIVY_CACHE_DIR}"

    echo "====++++ Displaying Trivy version ++++===="
    sh "trivy --version"

    echo "====++++ Downloading Trivy vulnerability database ++++===="
    retry(3) {
        sh "trivy image --download-db-only"
    }

    sh """
        if [ ! -f contrib/html.tpl ]; then
            mkdir -p contrib
            wget -O contrib/html.tpl https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl
        fi
    """

    def scanStatus
    try {
        echo "====++++ Running Trivy scan on Docker image ++++===="
        withCredentials([
            usernamePassword(
                credentialsId: 'gitAuth',
                usernameVariable: 'GHCR_USERNAME',
                passwordVariable: 'GHCR_TOKEN'
            )
        ]) {
            scanStatus = sh(
                script: """
                    trivy image \
                        --exit-code 1 \
                        --cache-dir ${env.TRIVY_CACHE_DIR} \
                        --severity ${params.TRIVY_SEVERITY} \
                        --no-progress \
                        --format template \
                        --template @contrib/html.tpl \
                        --output ${reportDir}/trivy-report.html \
                        --registry-username \$GHCR_USERNAME \
                        --registry-password \$GHCR_TOKEN \
                        ${env.IMAGE_NAME}
                """,
                returnStatus: true
            ).toString()
        }

        if (fileExists("${reportDir}/trivy-report.html")) {
            archiveArtifacts artifacts: "${reportDir}/trivy-report.html", fingerprint: true
            echo "Trivy report archived successfully."
        } else {
            error("Trivy report not generated at ${reportDir}/trivy-report.html.")
        }

        if (scanStatus.toInteger() == 0) {
            echo "No vulnerabilities found with severity ${params.TRIVY_SEVERITY}."
        } else if (scanStatus.toInteger() == 1) {
            echo "WARNING: Trivy found vulnerabilities at severity ${params.TRIVY_SEVERITY}. Exit code: ${scanStatus}"
        }
    } catch (Exception e) {
        unstable("Trivy scan encountered an error: ${e.getMessage()}")
        throw e
    }

    return scanStatus
}

/*
def uploadTrivyReportToS3() {
    echo "====++++ Uploading Trivy Report to S3 ++++===="
    def reportPath  = "trivy-reports/trivy-report.html"
    def s3Url       = "s3://${env.S3_BUCKET_NAME}/trivy-reports/trivy-report.html"

    def reportSize = fileExists(reportPath)
        ? sh(script: "wc -c < ${reportPath}", returnStdout: true).trim().toInteger()
        : 0

    if (reportSize > 0) {
        try {
            sh "aws s3 cp ${reportPath} ${s3Url}"
            echo "Trivy report uploaded successfully to ${s3Url}"
        } catch (Exception e) {
            error("Failed to upload Trivy report to S3: ${s3Url}. Error: ${e.getMessage()}")
        }
    } else {
        echo "Trivy report not found or empty; skipping S3 upload."
    }
}
*/

def pushDockerImage() {
    echo "====++++ Pushing Docker Image to Registry ++++===="
    def dockerRegistryUrl = 'https://index.docker.io/v1/'
    withDockerRegistry([credentialsId: 'docker_Id', url: dockerRegistryUrl]) {
        retry(3) {
            sh "docker push ${env.IMAGE_NAME}"

            sh """
               docker inspect --format='{{index .RepoDigests 0}}' ${env.IMAGE_NAME}
          """
        }
        echo "Docker image pushed successfully: ${env.IMAGE_NAME}"
    }
}

def smokeTest() {
    echo "====++++ Running Smoke Test ++++===="

    def hostPort      = params.HOST_PORT.toInteger()
    def containerPort = params.CONTAINER_PORT.toInteger()

    if (hostPort < 1 || hostPort > 65535) {
        error("HOST_PORT '${hostPort}' is not a valid port number (1-65535).")
    }
    if (containerPort < 1 || containerPort > 65535) {
        error("CONTAINER_PORT '${containerPort}' is not a valid port number (1-65535).")
    }

    try {
        sh """

            docker rm -f ${env.CONTAINER_NAME} || true

            docker run --name ${env.CONTAINER_NAME} \
                -d \
                -p ${hostPort}:${containerPort} \
                ${env.IMAGE_NAME}
        """
        sh "chmod +x ${WORKSPACE}/rhino-horn/check.sh"

        retry(10) {
            sleep(time: 10, unit: 'SECONDS')
            sh "APP_PORT=${hostPort} ${WORKSPACE}/rhino-horn/check.sh"
        }
    } catch (Exception e) {
        error("Smoke test failed: ${e.getMessage()}")
    }
}

def cleanup() {
    echo "====++++ Cleaning Up Docker Images and Containers ++++===="
    try {
        sh "docker stop  ${env.CONTAINER_NAME} 2>&1 || true"
        sh "docker rm -f ${env.CONTAINER_NAME} 2>&1 || true"
        sh "docker rmi -f ${env.IMAGE_NAME}    2>&1 || true"
        sh "docker ps -a"
    } catch (Exception e) {
        echo "WARNING: Cleanup encountered an unexpected error: ${e.getMessage()}"
    }
}

/*
def getEmailForUsers(String userIdsCsv) {
    def emailMap = [
        'user1': 'samuelhaddison71@gmail.com',
        'user2': 'orionhouse83@gmail.com'
    ]
    def userIds = userIdsCsv.split(',').collect { it.trim() }
    def emails = userIds.collect { id ->
        if (!emailMap.containsKey(id)) {
            error("Invalid recipient ID: ${id}")
        }
        return emailMap[id]
    }
    return emails.join(',')
}
*/

return this