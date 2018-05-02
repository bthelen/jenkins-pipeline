node {
    String projectAcronym = params['PROJECT_ACRONYM'] ?: error('Mandatory parameter PROJECT_ACRONYM was not provided.')
    String gitCloneUrl = params['GIT_CLONE_URL'] ?: error('Mandatory parameter GIT_CLONE_URL was not provided.')
    String gitCredentialsId = params['GIT_CREDENTIALS_ID'] ?: error('Mandatory parameter GIT_CREDENTIALS_ID was not provided.')

    String appPackagingType = params['APP_PACKAGING_TYPE'] ?: 'jar'
    String artifactSuffix = appPackagingType.toLowerCase()

    if (artifactSuffix != 'war' && artifactSuffix != 'jar') error('APP_PACKAGING_TYPE provided should be either war or jar.')

    String artifactoryServerId = params['ARTIFACTORY_SERVER_ID'] ?: 'artifactory_qa'
    String artifactoryBuildRepo = params['ARTIFACTORY_BUILD_REPO'] ?: 'lib-stapshot-local'
    String mvnToolId = params['MVN_TOOL_ID'] ?: 'Maven 3.0.5'

    String pcfDevCredentialsId = params['PCF_DEV_CREDENTIALS_ID'] ?: 'pcf-dev-space'
    String pcfDevApiTarget = params['PCF_DEV_API_TARGET'] ?: 'api.run.pivotal.io'
    String pcfDevOrg = params['PCF_DEV_ORG'] ?: 'org-name'
    String pcfDevSpace = params['PCF_DEV_SPACE'] ?: 'space-name'
    Boolean deployArtifact = params['DEPLOY_ARTIFACT'] ?: false

    stage('Preparation') {
        git credentialsId: gitCredentialsId, url: gitCloneUrl
        mvnHome = tool mvnToolId
    }

    stage('Build') {
        if (isUnix()) {
            sh "'${mvnHome}/bin/mvn' clean package"
        }
    }

    stage('Results') {
        junit '**/target/surefire-reports/TEST-*.xml'

        sh 'rm -rf release-archive tarball && mkdir -p release-archive tarball'
        sh "cp target/*.${artifactSuffix} release-archive/"
        sh "cp manifest.yml release-archive/"
        sh "cd release-archive && tar -czvf ../tarball/${projectAcronym}-build-${BUILD_NUMBER}.tgz . && cd -"

        archive 'tarball/*.tgz'
    }

    stage('Upload build to Artifactory') {
        def server = Artifactory.server artifactoryServerId
        def uploadSpec = """{
        "files":[
            {
                "pattern": "tarball/*.tgz",
                "target": "${artifactoryBuildRepo}/${projectAcronym}/new-builds/"
            }
        ]
        }"""
        server.upload(uploadSpec)
    }

    stage('Prepare next dev staging build in Artifactory') {
        sh "rm -rf dev-staging-tarball && mkdir -p dev-staging-tarball && cp tarball/*.tgz dev-staging-tarball/${projectAcronym}-dev-staging.tgz"
        def server = Artifactory.server artifactoryServerId
        def uploadSpec = """{
        "files":[
            {
                "pattern": "dev-staging-tarball/*.tgz",
                "target": "${artifactoryBuildRepo}/${projectAcronym}/cloud-dev-staging/"
            }
        ]
        }"""
        server.upload(uploadSpec)
    }

    stage('Get artifact from repository') {
        def server = Artifactory.server artifactoryServerId
        def downloadSpec = """{
        "files":[
            {
                "pattern": "${artifactoryBuildRepo}/${projectAcronym}/cloud-dev-staging/*.tgz",
                "target": "release-tarball/",
                "flat": true
            }
        ]
        }"""
        server.download(downloadSpec)

        sh 'tar -xvzf release-tarball/*.tgz'
        sh 'rm -rf target && mkdir -p target'
        sh "mv *.${appPackagingType} target/"
    }

    stage('Push to development environment on PCF') {
        if (deployArtifact) {
            timeout(time: 300, unit: 'SECONDS') {
                pushToCloudFoundry(
                        target: pcfDevApiTarget,
                        organization: pcfDevOrg,
                        cloudSpace: pcfDevSpace,
                        credentialsId: pcfDevCredentialsId
                )
            }
        }
    }
}
