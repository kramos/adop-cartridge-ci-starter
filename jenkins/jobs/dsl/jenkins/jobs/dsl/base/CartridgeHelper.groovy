package jenkins.jobs.dsl.base

/**
 * Re-usable goodness for building Jenkins jobs
 */
class CartridgeHelper {

    /**
     * Creates useful base job
     *
     * @dslFactory base DSL file
     * @jobName Jenkins job name
     * @variables reuired variables
     */
    static baseCartridgeJob(dslFactory, jobName, variables) {
        dslFactory.freeStyleJob(jobName) {
            label(variables.buildSlave)
            environmentVariables {
                env('WORKSPACE_NAME', variables.workspaceFolderName)
                env('PROJECT_NAME', variables.projectFolderName)
                env('ABSOLUTE_JENKINS_HOME', variables.absoluteJenkinsHome)
                env('ABSOLUTE_JENKINS_SLAVE_HOME', variables.absoluteJenkinsSlaveHome)
                env('ABSOLUTE_WORKSPACE', variables.absoluteWorkspace)
            }
            wrappers {
                sshAgent(variables.sshAgentName)
                timestamps()
                maskPasswords()
                colorizeOutput()
                preBuildCleanup()
                injectPasswords {
                    injectGlobalPasswords(false)
                    maskPasswordParameters(true)
                }
            }
            logRotator {
                numToKeep(variables.logRotatorNum)
                artifactNumToKeep(variables.logRotatorArtifactNum)
                daysToKeep(variables.logRotatorDays)
                artifactDaysToKeep(variables.logRotatorArtifactDays)
            }
        }
    }

    /**
     * Creates the get from SCM job
     *
     * @job a base job that will be extended
     * @variables variables required configuration
     */
    static getBuildFromSCMJob(dslFactory, jobName, variables) {
        def job = baseCartridgeJob(dslFactory, jobName, variables)
        job.with {
            description('This job downloads a ' + variables.artifactName.toLowerCase() + '. Also job have Gerrit trigger with regexp configuration to capture events "' + variables.artifactName.toLowerCase() + '" in the repository name.')
            parameters {
                stringParam("REPO", variables.artifactDefaultValue, 'Name of the ' + variables.artifactName.toLowerCase() + ' you want to load')
            }
            environmentVariables {
                env('WORKSPACE_NAME', variables.workspaceFolderName)
                env('PROJECT_NAME', variables.projectFolderName)
                groovy('''
        if (!binding.variables.containsKey('GERRIT_PROJECT')) {
            return [GERRIT_PROJECT: "''' + variables.projectFolderName + '''/${REPO}"]
        }'''.stripMargin())
            }
            scm {
                git {
                    remote {
                        url(variables.gitUrl)
                        credentials(variables.gitCredentials)
                    }
                    branch('*/' + variables.gitBranch)
                }
            }
            configure { node ->
                node / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
                    'string' '*'
                }
            }
            steps {
                shell('''#!/bin/bash -xe
                    |git_data=$(git --git-dir "${WORKSPACE}/.git" log -1 --pretty="format:%an<br/>%s%b")
                    |echo "GIT_LOG_DATA=${git_data}" > git_log_data.properties
                    '''.stripMargin())
                environmentVariables {
                    propertiesFile('git_log_data.properties')
                }
            }
            steps {
                systemGroovyCommand('''
                                |import hudson.model.*;
                                |import hudson.util.*;
                                |
                                |// Get current build number
                                |def currentBuildNum = build.getEnvironment(listener).get('BUILD_NUMBER')
                                |println "Build Number: " + currentBuildNum
                                |
                                |// Get Git Data
                                |def gitData = build.getEnvironment(listener).get('GIT_LOG_DATA')
                                |println "Git Data: " + gitData;
                                |
                                |def currentBuild = Thread.currentThread().executable;
                                |def oldParams = currentBuild.getAction(ParametersAction.class)
                                |
                                |// Update the param
                                |def params = [ new StringParameterValue("T",gitData), new StringParameterValue("B",currentBuildNum) ]
                                |
                                |// Remove old params - Plugins inject variables!
                                |currentBuild.actions.remove(oldParams)
                                |currentBuild.addAction(new ParametersAction(params));
                                '''.stripMargin())
            }
            triggers {
                gerrit {
                    events {
                        refUpdated()
                    }
                    project('reg_exp:' + variables.gerritTriggerRegExp, 'plain:master')
                    configure { node ->
                        node / serverName('ADOP Gerrit')
                    }
                }
            }
            publishers {
                archiveArtifacts {
                    pattern('**/*')
                }
                downstreamParameterized {
                    trigger(variables.triggerDownstreamJob) {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('B', variables.nextCopyArtifactsFromBuild)
                        }
                    }
                }
            }
        }
        return job
    }

    /**
     * Creates a generic job performing a shell command 
     *
     * @job a base job that will be extended
     * @variables variables required configuration
     */
    static getShellJob(dslFactory, jobName, variables) {
        def job = baseCartridgeJob(dslFactory, jobName, variables)
        if (!variables.containsKey("jobCommand")) {
            variables.jobCommand = 'echo TODO - add automation here...'
        }
        job.with {
            description(variables.jobDescription)
            parameters {
                stringParam('B', '', 'Parent build job number')
            }
            steps {
                copyArtifacts(variables.copyArtifactsFromJob) {
                    buildSelector {
                        buildNumber('${B}')
                    }
                }
                shell('''#!/bin/bash -xe
                        |'''.stripMargin() + variables.jobCommand.stripMargin())
            }

            publishers {
                archiveArtifacts {
                    pattern('**/*')
                }
            }
        }

        if (variables.containsKey("manualTrigger")) {
            job.with {
                publishers {
                    buildPipelineTrigger(variables.triggerDownstreamJob) {
                        parameters {
                            predefinedProp('B', variables.nextCopyArtifactsFromBuild)
                        }
                    }
                }
            }
        } else {
            job.with {
                publishers {
                    downstreamParameterized {
                        trigger(variables.triggerDownstreamJob) {
                            condition('UNSTABLE_OR_BETTER')
                            parameters {
                                predefinedProp('B', variables.nextCopyArtifactsFromBuild)
                            }
                        }
                    }
                }
            }
        }
        return job

    }

    /**
     * Creates a generic job performing a shell command using auth
     *
     * @job a base job that will be extended
     * @variables variables required configuration
     */
    static getShellAuthJob(dslFactory, jobName, variables) {
        def jobShellJob = getShellJob(dslFactory, jobName, variables)
        jobShellJob.with {
            parameters {
                credentialsParam("JENKINS_LOGIN"){
                    type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
                    required()
                    defaultValue('int-job-credentials')
                    description('A Jenkins username and password for triggering the integration job. Please make sure the credentials are added with ID "int-job-credentials"')
                }
            }
            wrappers {
                credentialsBinding {
                    usernamePassword("USERNAME_JENKINS", "PASSWORD_JENKINS", '${JENKINS_LOGIN}')
                }
            }
   
        }
        return jobShellJob
    }

    /**
     * Creates an integration job
     *
     * @job a base job that will be extended
     * @jobName job name required
     * @variables variables required configuration
     */
    static getIntegratedJob(dslFactory, jobName, variables) {

        def jobIntjob = dslFactory.freeStyleJob(jobName) {
            label(variables.buildSlave)
            environmentVariables {
                env('WORKSPACE_NAME', variables.workspaceFolderName)
                env('PROJECT_NAME', variables.projectFolderName)
                env('ABSOLUTE_JENKINS_HOME', variables.absoluteJenkinsHome)
                env('ABSOLUTE_JENKINS_SLAVE_HOME', variables.absoluteJenkinsSlaveHome)
                env('ABSOLUTE_WORKSPACE', variables.absoluteWorkspace)
            }
            wrappers {
                sshAgent(variables.sshAgentName)
                timestamps()
                maskPasswords()
                colorizeOutput()
                injectPasswords {
                    injectGlobalPasswords(false)
                    maskPasswordParameters(true)
                }
            }
            logRotator {
                numToKeep(variables.logRotatorNum)
                artifactNumToKeep(variables.logRotatorArtifactNum)
                daysToKeep(variables.logRotatorDays)
                artifactDaysToKeep(variables.logRotatorArtifactDays)
            }

            description(variables.jobDescription)
            parameters {
                stringParam('COMPONENT_NAME', '', 'Name of component being integrated')
                stringParam('COMPONENT_BUILD_NUMBER', '', 'Build number of component being integrated')
            }
            steps {
                shell('''#!/bin/bash -xe
                        |echo Creating a new Integration build to include version: $COMPONENT_BUILD_NUMBER of component: $COMPONENT_NAME
                        |
                        |int_fn=integration.txt
                        |set +e
                        |rm ${JOB_BASE_NAME}_*.txt
                        |grep "Created:" $int_fn 2>&1 /dev/null
                        |if [ $? -ne 0 ]
                        |then
                        |        echo Created: `date` >> $int_fn
                        |else
                        |        sed -i -e "s/Created:.*/Created: `date`/" $int_fn
                        |fi
                        |
                        |grep "${COMPONENT_NAME}[^\\s-]*=" $int_fn 2>&1 /dev/null
                        |if [ $? -eq 0 ]
                        |then
                        |    sed -i -e "/${COMPONENT_NAME}[^\\s]*=.*/d" $int_fn
                        |fi
                        |set -e
                        |
                        |echo $COMPONENT_NAME=$COMPONENT_BUILD_NUMBER >> $int_fn
                        |
                        |echo
                        |cat $int_fn
                        |
                        |cp $int_fn ${JOB_BASE_NAME}_${BUILD_NUMBER}.txt
                        |'''.stripMargin())
            }

            publishers {
                archiveArtifacts {
                    pattern('**/*')
                }
            }
        }

        if (variables.containsKey("manualTrigger")) {
            jobIntjob.with {
                publishers {
                    buildPipelineTrigger(variables.triggerDownstreamJob) {
                        parameters {
                            predefinedProp('B', variables.nextCopyArtifactsFromBuild)
                        }
                    }
                }
            }
        } else {
            jobIntjob.with {
                publishers {
                    downstreamParameterized {
                        trigger(variables.triggerDownstreamJob) {
                            condition('UNSTABLE_OR_BETTER')
                            parameters {
                                predefinedProp('B', variables.nextCopyArtifactsFromBuild)
                            }
                        }
                    }
                }
            }
        }
        return jobIntjob

    }

    /**
     * Creates a pipeline view
     *
     * @dslFactory base DSL file
     * @viewName Name of view
     * @jobName Name of first job in pipeline
     * @viewTitle Title of view
     */
    static basePipelineView(dslFactory, viewName, jobName, viewTitle) {
        dslFactory.buildPipelineView(viewName) {
            title(viewTitle)
            displayedBuilds(5)
            refreshFrequency(5)
            selectedJob(jobName)
            showPipelineParameters()
            showPipelineDefinitionHeader()
        }
    }

}
