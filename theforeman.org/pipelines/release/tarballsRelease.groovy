pipeline {
    agent { label 'sshkey' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        version = env.getProperty('version')
        major_version = env.getProperty('major_version')
        ruby_ver = '3.0.4'
    }

    stages {
        stage('Parallel') {

            parallel {
                stage('Foreman') {
                    steps {
                        verify_tag('foreman', version)
                        build_tarball('foreman', version, ruby_ver)
                    }
                }
                stage('Smart Proxy') {
                    steps {
                        verify_tag('smart-proxy', version)
                        build_tarball('smart-proxy', version, ruby_ver)
                    }
                }
                stage('Foreman SELinux') {
                    steps {
                        verify_tag('foreman-selinux', version)
                        build_tarball('foreman-selinux', version, ruby_ver)
                    }
                }
                stage('Foreman Installer') {
                    steps {
                        verify_tag('foreman-installer', version)
                        build_tarball('foreman-installer', version, ruby_ver)
                    }
                }
            }
        }
    }
    post {
        cleanup {
            deleteDir()
        }
    }
}

void verify_tag(project, version) {
    dir(project) {
        git url: "https://github.com/theforeman/${project}.git", branch: 'develop', poll: false
        sh "git tag -l ${version} | grep ${version}"
    }
}

void build_tarball(project, version, ruby_ver) {
    def base_dir = "/var/www/vhosts/downloads/htdocs/${project}"
    def rake = "rake"

    dir(project) {
        checkout scm: [$class: 'GitSCM',
            userRemoteConfigs: [[url: "https://github.com/theforeman/${project}.git"]],
            branches: [[name: "refs/tags/${version}"]],
            extensions: [[$class: 'CleanCheckout']]],
            changelog: false,
            poll: false

        if (project == 'foreman') {
            sh "cat config/settings.yaml.example > config/settings.yaml"
            sh "cat config/database.yml.example > config/database.yml"
        }

        env.setProperty('DEBUG_RESOLVER', '1')

        if (fileExists("Gemfile")) {
            bundleInstall(ruby_ver, "--without=development")
        }

        if (project == 'foreman-installer') {
            bundleExec(ruby_ver, "rake clean")
        }

        bundleExec(ruby_ver, "rake pkg:generate_source")

        sshagent(['deploy-downloads']) {
            sh "rsync -v --ignore-existing pkg/* downloads@website01.osuosl.theforeman.org:${base_dir}/"
        }
    }
}
