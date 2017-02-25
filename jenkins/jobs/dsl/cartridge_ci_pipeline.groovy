import jenkins.jobs.dsl.base.CartridgeHelper

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}/Cartridge_Management/cartridge_ci_pipeline"
def appName = "Cartridge"

// Creating folder to house the cartridge ci
def FolderDisplayName = "cartridge_ci_pipeline"
def FolderDescription = "CI pipeline for the cartridge"
def cartridgeFolder = folder(projectFolderName) {
  displayName(FolderDisplayName)
  description(FolderDescription)
}

// Variables
def variables = [
    gitUrl                  : 'ssh://jenkins@gerrit:29418/${GERRIT_PROJECT}',
    gitBranch               : 'master',
    gitCredentials          : 'adop-jenkins-master',
    gerritTriggerRegExp     : (projectFolderName + '/adop-cartridge-ci-starter').replaceAll("/", "\\\\/"),
    projectNameKey          : (projectFolderName.toLowerCase().replace("/", "-")),
    buildSlave              : 'docker',
    artifactName            : 'adop-cartridge-ci-starter',
    sshAgentName            : 'adop-jenkins-master',
    logRotatorNum           : 10,
    logRotatorArtifactNum   : 3,
    logRotatorDays          : -1,
    logRotatorArtifactDays  : -1,
    projectFolderName       : projectFolderName,
    workspaceFolderName     : workspaceFolderName,
    absoluteJenkinsHome     : '/var/lib/docker/volumes/jenkins_home/_data',
    absoluteJenkinsSlaveHome: '/var/lib/docker/volumes/jenkins_slave_home/_data',
    absoluteWorkspace       : '${ABSOLUTE_JENKINS_SLAVE_HOME}/${JOB_NAME}/'
]

// Jobs
def pullSCM = CartridgeHelper.getBuildFromSCMJob(this, projectFolderName + '/Get_' + appName + '_Source_Code', variables + [
        'artifactDefaultValue': 'adop-cartridge-ci-starter',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Validate',
        'nextCopyArtifactsFromBuild': '${BUILD_NUMBER}',
    ]
)

def validateJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_Validate', variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_' + appName + '_Source_Code',
        'nextCopyArtifactsFromBuild': '${BUILD_NUMBER}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Unit_Test',
        'jobDescription': 'This job validates the cartridge',
        'jobCommand': '''#!/bin/bash -e
                        |echo
                        |echo
                        |
                        |# Checking for existence of files
                        |EXPECTEDFILES="README.md metadata.cartridge src/urls.txt"
                        |for var in ${EXPECTEDFILES}
                        |do
                        |
                        |  if [ -f "${var}" ]; then
                        |    echo "Pass: file ${var} exists."
                        |  else
                        |    echo "Fail: file ${var} does not exist."
                        |    exit 1
                        |  fi
                        |done
                        |
                        |# Checking for existence of directories
                        |EXPECTEDDIRS="infra jenkins jenkins/jobs jenkins/jobs/dsl jenkins/jobs/xml src"
                        |for var in ${EXPECTEDDIRS}
                        |do
                        |
                        |  if [ -d "${var}" ]; then
                        |    echo "Pass: directory ${var} exists."
                        |  else
                        |    echo "Fail: directory ${var} does not exist."
                        |    exit 1
                        |  fi
                        |done
                        |
                        |# Checking for existence of Jenkins job configs
                        |GCODE=0
                        |cd ${WORKSPACE}/jenkins/jobs/dsl
                        |if ls -la | awk '{ print $9}' | grep .groovy; then
                        |GCODE=1
                        |fi
                        |
                        |XCODE=0
                        |cd ${WORKSPACE}/jenkins/jobs/xml
                        |if ls -la | awk '{ print $9}' | grep .xml; then
                        |XCODE=1
                        |fi
                        |
                        |if [ $GCODE -eq 1 ]; then
                        |	echo "Pass: Jenkins job (Groovy) config exists."
                        |elif [ $GCODE -eq 0 ] && [ $XCODE -eq 1 ]; then
                        |	echo "Pass: Jenkins job (XML) config exists."
                        |	echo "Note: It is recommended that Groovy is used in favour of XML."
                        |else
                        |	echo "Fail: Jenkins job configs do not exist."
                        |	exit 1
                        |fi
                        |
                        |
                        |echo
                        |echo PASSED!
                        |echo'''
    ]
)

def unitTestJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_Unit_Test', variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_' + appName + '_Source_Code',
        'nextCopyArtifactsFromBuild': '${B}',
        'triggerDownstreamJob': projectFolderName + '/' + appName + '_Reload',
        'jobDescription': 'This job runs any unit tests',
        'jobCommand': '''#!/bin/bash -xe
                        |if [ -e "gradlew" ]; then
                        |chmod +x ./gradlew
                        |    ./gradlew test
                        |fi''',
        'manualTrigger': 'true'
    ]
)

def reloadJob = CartridgeHelper.getShellJob(this, projectFolderName + '/' + appName + '_Reload', variables + [
        'copyArtifactsFromJob': projectFolderName + '/Get_' + appName + '_Source_Code',
        'nextCopyArtifactsFromBuild': '${B}',
        'jobDescription': 'This reloads the cartridge',
    ]
)

// Views
def rolePipelineView = CartridgeHelper.basePipelineView(
    this,
    projectFolderName + '/' + appName + '_Cartridge_CI',
    projectFolderName + '/Get_' + appName + '_Source_Code',
    'CI pipeline for this cartridge"' + appName + '".'
)

