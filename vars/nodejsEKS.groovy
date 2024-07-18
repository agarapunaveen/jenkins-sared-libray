def call (Map configMap){

    pipeline {
        agent {
            label 'agent-1'
        }
        options {
            // Timeout counter starts AFTER agent is allocated
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
    
        environment{
        def appVersion = ''
        def nexusUrl= pipelineGlobals.nexusURL()
        def region= pipelineGlobals.region()
        def account_id=pipelineGlobals.accountId()
        def component=configMap("component")
        def project=configMap("project")
        }
        stages {
            stage('read the version'){
                steps{

                script { 
                    def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                    
                    }
                }
            }
            stage('Install dependencies') {
                steps {
                    sh """
                    npm install
                    ls -ltr
                    """
                }
            }
            stage('Build'){
                steps{
                sh """
                        zip -q -r ${component}-${appVersion}.zip * -x Jenkinsfile -x ${component}-${appVersion}.zip
                        ls -ltr
                    """
                }
            }

            stage ('Docker Build'){
                steps{
                    sh """
                    aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com

                    docker build -t ${project}-${component} .

                    docker tag ${project}-${component}:latest ${account_id}.dkr.ecr.${region}.amazonaws.com/expense-${component}:${appVersion}

                    docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion}
                    """
                }
            }

            stage ('Deploy'){
                steps{
                    sh """
                    aws eks update-kubeconfig --region ${region} --name ${project}-dev
                    cd helm
                    sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                    helm install ${component} -n ${project} . 
                    """
                }
            }

            // stage('sonar scanar'){
            //     environment {
            //         scannerHome = tool 'sonar'
            //     }
            //     steps {
            //         script {
            //             withSonarQubeEnv('sonar') {
            //                 sh "${scannerHome}/bin/sonar-scanner"
                            
            //             }
            //         }
            //     }
            // }

            // stage('nexus artifact upload'){
            //     steps{
            //       script{
            //         nexusArtifactUploader(
            //         nexusVersion: 'nexus3',
            //         protocol: 'http',
            //         nexusUrl: "${nexusUrl}",
            //         groupId: 'com.example',
            //         version: "${appVersion}",
            //         repository: "backend",
            //         credentialsId: 'nexus',
            //         artifacts: [
            //             [artifactId: "backend",
            //             classifier: '',
            //             file: 'backend-' + "${appVersion}" + '.zip',
            //             type: 'zip']
            //         ]
            //     )
            //       }
            //     }
            // }
            // stage("deploy"){
            //     steps{
                
            //         script{
            //              def params =[
            //                string(name: 'appVersion',value: "${appVersion}")
            //             ]
            //              build job: 'backend-deploy', parameters: params,wait: false
            //         }
            //     }
            // }
        }
            post {
                always{
                    echo ' i will always say hello again'
                    deleteDir()
                }
                success{
                    echo ' i will run the pipeline success'
                }
                failure{
                    echo ' i will run the pipeline failure'
                }
            }
    }
}