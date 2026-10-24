// forge-admin CI 流水线(通用形态,接入任意 Jenkins 即用;Gitee Go 可参考各 stage 命令)
pipeline {
    agent any
    stages {
        stage('Backend') {
            steps {
                sh 'cd apps/forge-server && mvn -B test'
            }
        }
        stage('Frontend') {
            steps {
                sh '''
                    cd apps/forge-web
                    corepack enable || true
                    pnpm install --prefer-offline
                    CI=true npx vitest run --reporter=basic
                    npx vue-tsc --noEmit
                    pnpm lint
                    pnpm build
                '''
            }
        }
    }
    post {
        failure {
            echo 'CI 失败:后端 mvn test 或前端四命令未通过'
        }
    }
}
