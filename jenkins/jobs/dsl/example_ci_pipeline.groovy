import jenkins.jobs.dsl.base.CartridgeHelper

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"
def appName = "ExampleApp"

// Variables
def variables = [
    gitUrl                  : 'ssh://jenkins@gerrit:29418/${GERRIT_PROJECT}',
    gitBranch               : 'master',
    gitCredentials          : 'adop-jenkins-master',
    gerritTriggerRegExp     : (projectFolderName + '/spring-music').replaceAll("/", "\\\\/"),
    projectNameKey          : (projectFolderName.toLowerCase().replace("/", "-")),
    buildSlave              : 'docker',
    artifactName            : 'spring-music',
    sshAgentName            : 'adop-jenkins-master',
    logRotatorNum           : 10,
    logRotatorArtifactNum   : 3,
    logRotatorDays          : -1,
    logRotatorArtifactDays  : -1,
    projectFolderName       : projectFolderName,
    workspaceFolderName     : workspaceFolderName,
    absoluteJenkinsHome     : '/var/lib/docker/volumes/jenkins_home/_data',
    absoluteJenkinsSlaveHome: '/var/lib/docker/volumes/jenkins_slave_home/_data',
    absoluteWorkspace       : '${ABSOLUTE_JENKINS_SLAVE_HOME}/${JOB_NAME}/',
    cfCliImage              : 'kramos/cloud-foundry-cli',
    gradleImage             : 'kramos/docker-gradle',
    cloudFoundryLib         :  'api.run.pivotal.io api.ng.bluemix.net'
]

// Jobs
def pullSCM = CartridgeHelper.getBuildFromSCMJob(this, projectFolderName + '/Get_' + appName + '_Source_Code', variables + [
        'artifactDefaultValue': 'spring-music',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Build',
        'nextCopyArtifactsFromBuild': '${BUILD_NUMBER}',
    ]
)

def buildAppJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_Build', variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_' + appName + '_Source_Code',
        'nextCopyArtifactsFromBuild': '${BUILD_NUMBER}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Unit_Static',
        'jobDescription': 'This job builds the application'
    ]
)


def unitStaticJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_Unit_Static', variables + [
        'copyArtifactsFromJob': projectFolderName + '/' + appName + '_Build',
        'nextCopyArtifactsFromBuild': '${B}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_ST_Deploy',
        'jobDescription': 'This job runs unit tests and static code analysis agaisnt our application',
    ]
)

def stDeployJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_ST_Deploy', variables + [
        'copyArtifactsFromJob': projectFolderName + '/' + appName + '_Build',
        'nextCopyArtifactsFromBuild': '${B}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_ST_Test',
        'jobDescription': 'This job deploys our application to the ST environment.',
    ]
)


def stTestJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_ST_Test', variables + [
        'copyArtifactsFromJob': projectFolderName + '/' + appName + '_Build',
        'nextCopyArtifactsFromBuild': '${B}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Publish',
        'jobDescription': 'This job runs the functional testing in the ST environment',
    ]
)


def downstreamName
try {
  downstreamName = "${DOWNSTREAM_FOLDER}"
} catch (Exception e) {
  lastJob = null
}
if ((downstreamName != null)) {
    def publish = CartridgeHelper.getShellAuthJob(this, projectFolderName + '/' + appName + '_Publish', variables + [
            'copyArtifactsFromJob': projectFolderName + '/' + appName + '_Build',
            'nextCopyArtifactsFromBuild': '${B}',
            'triggerDownstreamJob': projectFolderName + '/TODO',
            'jobDescription': 'This publishes a integration build candidate',
            'jobCommand': 'FOLDER=`echo ' + projectFolderName + ''' | sed "s/\\//\\/job\\//g"`; 
                          |set +x
                          |echo TRIGGERING INTEGRATION BUILD 
                          |echo
                          |docker exec jenkins curl -s -X POST ${USERNAME_JENKINS}:${PASSWORD_JENKINS}@localhost:8080/jenkins/job/${FOLDER}/job/''' + downstreamName + '/job/Integrated_Build/buildWithParameters?COMPONENT_NAME=Integrated_Build\\&COMPONENT_BUILD_NUMBER=${B}',
        ]
    )
}

// Views
def rolePipelineView = CartridgeHelper.basePipelineView(
    this,
    projectFolderName + '/' + appName + '_CI',
    projectFolderName + '/Get_' + appName + '_Source_Code',
    'Example CI pipeline for "' + appName + '".'
)

