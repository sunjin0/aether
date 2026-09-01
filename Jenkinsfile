pipeline {
    agent any
    triggers { githubPush() }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Validate Flyway Migrations') {
            steps {
                sh '''
                    set -eu
                    find api/src/main/resources/db/migration/postgresql -type f -name 'V*__*.sql' -print \
                      | sed 's#.*/##' \
                      | awk 'match($0, /^V[0-9]+__[-A-Za-z0-9_]+\\.sql$/) { print substr($0, 2, index($0, "__") - 2); next } { print "INVALID:" $0 }' \
                      | tee /tmp/aether-flyway-versions.txt
                    if grep -q '^INVALID:' /tmp/aether-flyway-versions.txt; then exit 1; fi
                    if [ "$(sort /tmp/aether-flyway-versions.txt | uniq -d | wc -l)" -ne 0 ]; then
                        echo 'Duplicate Flyway migration version detected'
                        exit 1
                    fi
                '''
            }
        }

        stage('Build Admin') {
            steps {
                // 设置github处理中状态
                 script{
                            step([$class: 'GitHubCommitStatusSetter',
                                                  contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'jenkins-ci'],
                                                  statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: '处理中...', state: 'PENDING']]]])
                 }
                sh 'echo "后台服务构建开始..."'
                sh 'mvn clean package -pl admin -am'
                sh 'echo "后台服务构建完成..."'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'echo "构建 Docker 镜像..."'
                // 为 admin 服务构建 Docker 镜像
                sh 'docker build -t admin-service:latest admin/'
                sh 'echo "Docker 镜像构建完成..."'
            }
        }

        stage('Deploy to Docker') {
            steps {
                sh 'echo "部署到 Docker 容器..."'
                // 停止并删除现有容器（如果存在）
                sh 'docker stop admin-container || true'
                sh 'docker rm admin-container || true'
                // 运行新的容器
                sh 'docker run -d --name admin-container -p 8080:8080 admin-service:latest'
                sh 'echo "部署完成..."'
            }
        }
    }

    post {
        success {
            script {
                step([$class: 'GitHubCommitStatusSetter',
                      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'jenkins-ci'],
                      statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: '测试环境发布成功', state: 'SUCCESS']]]])
            }
        }
        failure {
            script {
                step([$class: 'GitHubCommitStatusSetter',
                      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'jenkins-ci'],
                      statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: '测试环境发布失败', state: 'FAILURE']]]])
            }
        }
    }
}
