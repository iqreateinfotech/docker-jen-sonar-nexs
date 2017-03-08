def gitUrl = 'git@128.199.87.138:root/pepcore.git'

createCiJob("peptide-app", gitUrl, "build.xml")
createSonarJob("peptide-app", gitUrl, "build.xml")
createDockerBuildJob("peptide-app", "app")
createDockerStartJob("peptide-app", "app", "48080")
createDockerStopJob("peptide-app", "app")

createCiJob("peptide-app-monitoring", gitUrl, "monitoring/pom.xml")
createSonarJob("peptide-app-monitoring", gitUrl, "monitoring/pom.xml")
createDockerBuildJob("peptide-app-monitoring", "monitoring")
createDockerStartJob("peptide-app-monitoring", "monitoring", "58080")
createDockerStopJob("peptide-app-monitoring", "monitoring")

def createCiJob(def jobName, def gitUrl, def antFile) {
  job("${jobName}-1-ci") {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
      stringParam("REPOSITORY_URL", "http://nexus:8081/content/repositories/releases/", "Nexus Release Repository URL")
    }
    scm {
      git {
        remote {
          url(gitUrl)
        }
        extensions {
          cleanAfterCheckout()
        }
      }
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    triggers {
      scm('30/H * * * *')
      githubPush()
    }
    steps {
      ant {
          goals('ant versions:set -DnewVersion=DEV-\${BUILD_NUMBER}')
          mavenInstallation('ant')
          rootPOM( antFile )
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings('MyGlobalSettings')
      }
      
    }
    publishers {
      chucknorris()
      archiveXUnit {
        jUnit {
          pattern('build/logs/junit.xml')
          skipNoTestFiles(true)
          stopProcessingIfError(true)
        }
      }
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      downstreamParameterized {
        trigger("${jobName}-2-sonar") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createSonarJob(def jobName, def gitUrl, def antFile) {
  job("${jobName}-2-sonar") {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
    }
    scm {
      cloneWorkspace("${jobName}-1-ci")
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    steps {
      ant {
      
        goals('org.sonar.plugins.clover.CloverPlugin:2.4:prepare-agent install -Psonar')
        mavenInstallation('ant')
        rootPOM(antFile)
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
      ant {
        goals('ant sonar')
        mavenInstallation('ant')
        rootPOM(antFile)
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
    }
    publishers {
      chucknorris()
      downstreamParameterized {
        trigger("${jobName}-3-docker-build") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createDockerBuildJob(def jobName, def folder) {

  println "############################################################################################################"
  println "Creating Docker Build Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-3-docker-build") {
    logRotator {
        numToKeep(10)
    }
    scm {
      cloneWorkspace("${jobName}-1-ci")
    }
    steps {
      steps {
        shell("cd ${folder} && sudo /usr/bin/docker build -t peptide-${folder} .")
      }
    }
    publishers {
      chucknorris()
      downstreamParameterized {
        trigger("${jobName}-4-docker-start-container") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createDockerStartJob(def jobName, def folder, def port) {

  println "############################################################################################################"
  println "Creating Docker Start Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-4-docker-start-container") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell('echo "Stopping Docker Container first"')
        shell("sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter=\"name=peptide-${folder}\") | true ")
        shell("sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter=\"name=peptide-${folder}\") | true ")
        shell('echo "Starting Docker Container"')
        shell("sudo /usr/bin/docker run -d --name peptide-${folder} -p=${port}:8080 peptide-${folder}")
      }
    }
    publishers {
      chucknorris()
    }
  }
}

def createDockerStopJob(def jobName, def folder) {

  println "############################################################################################################"
  println "Creating Docker Stop Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-5-docker-stop-container") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell("sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter=\"name=peptide-${folder}\")")
        shell("sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter=\"name=peptide-${folder}\")")
      }
    }
    publishers {
      chucknorris()
    }
  }
}

buildPipelineView('Pipeline') {
    filterBuildQueue()
    filterExecutors()
    title('PEPTIDE App CI Pipeline')
    displayedBuilds(5)
    selectedJob("peptide-app-1-ci")
    alwaysAllowManualTrigger()
    refreshFrequency(60)
}

listView('PEPTIDE App') {
    description('')
    filterBuildQueue()
    filterExecutors()
    jobs {
        regex(/peptide-app-.*/)
    }
    columns {
        status()
        buildButton()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
    }
}
 
