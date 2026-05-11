void git_clone_foreman_infra(args = [:]) {
    target_dir = args.target_dir ?: ''

    dir(target_dir) {
        git url: 'https://github.com/theforeman/foreman-infra', poll: false
    }
}

void git_clone_jenkins_jobs(args = [:]) {
    target_dir = args.target_dir ?: ''

    dir(target_dir) {
        git url: 'https://github.com/theforeman/jenkins-jobs', poll: false
    }
}

def list_files(glob = '') {
    sh(script: "ls -1 ${glob}", returnStdout: true, label: "list files: '${glob}'").trim().split()
}

def runDuffyPipeline(project, version, expected_version = '') {
    def parts = project.tokenize('-')
    def actual_type = parts.size() > 1 ? parts[0..-2].join('-') : project
    runDuffyTest(
        pipelines: pipelines,
        type: actual_type,
        version: version,
        expected_version: expected_version
    )
}

def runDuffyPipelines(projects, version, expected_version = '') {
    def branches = [:]
    projects.each { project ->
        def parts = project.tokenize('-')
        def actual_type = parts.size() > 1 ? parts[0..-2].join('-') : project
        branches["${project}-${version}"] = {
            runDuffyTest(
                pipelines: pipelines,
                type: actual_type,
                version: version,
                expected_version: expected_version
            )
        }
    }
    parallel branches
}

def notifyDiscourse(env, introText, description) {
    emailext(
        subject: "${env.JOB_NAME} ${env.BUILD_ID} failed",
        to: 'ci@community.theforeman.org',
        body: [introText, env.BUILD_URL, description].minus(null).join('\n\n')
    )
}
