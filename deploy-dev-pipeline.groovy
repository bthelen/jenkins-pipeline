node {
    String projectAcronym = params['PROJECT_ACRONYM'] ?: error('Mandatory parameter PROJECT_ACRONYM was not provided.')
    String appPackagingType = params['APP_PACKAGING_TYPE'] ?: 'jar'
    String artifactSuffix = appPackagingType.toLowerCase()

    if (artifactSuffix != 'war' && artifactSuffix != 'jar') error('APP_PACKAGING_TYPE provided should be either war or jar.')

    String artifactoryServerId = params['ARTIFACTORY_SERVER_ID'] ?: 'artifactory_qa'
    String artifactoryBuildRepo = params['ARTIFACTORY_BUILD_REPO'] ?: 'libs-stapshot-local'

    String pcfDevCredentialsId = params['PCF_DEV_CREDENTIALS_ID'] ?: 'pcf-dev-space'
//todo fix below
    String pcfDevApiTarget = params['PCF_DEV_API_TARGET'] ?: 'api.run.pivotal.io'
    String pcfDevOrg = params['PCF_DEV_ORG'] ?: 'org-name'
    String pcfDevSpace = params['PCF_DEV_SPACE'] ?: 'space-name'

    stage('Get artifact from repository'){
        sh 'rm -rf release-tarball && mkdir -p release-tarball'
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

    stage('Push to development environment on PCF'){
        timeout(time: 300, unit: 'SECONDS'){
             pushToCloudFoundry(
                    target: pcfDevApiTarget,
                    organization: pcfDevOrg,
                    cloudSpace: pcfDevSpace,
                    credentialsId: pcfDevCredentialsId
            )
        }
    }
}
