def status_copr_links(repo) {
    if(fileExists('copr_build_info')) {
        def build_info_files = sh(returnStdout: true, script: "ls copr_build_info/", label: "copr build info").trim().split("\n")

        for(String build_info_file: build_info_files) {
            build_info_yaml = readYaml(file: "copr_build_info/${build_info_file}")

            for(Map build_info: build_info_yaml['results']) {
                githubNotify(
                    credentialsId: 'github-login',
                    account: 'theforeman',
                    repo: repo,
                    sha: "${ghprbActualCommit}",
                    context: "copr/${build_info_file}-${build_info['chroot']}",
                    description: "Copr build",
                    status: build_info["failed"] ? 'FAILURE' : 'SUCCESS',
                    targetUrl: build_info['build_urls'][0]
                )
            }
        }
    }
}

def copr_repos(package_name) {
    def repos = []

    if(fileExists('copr_build_info')) {
        build_info_yaml = readYaml(file: "copr_build_info/${package_name}")

        for(Map build_info: build_info_yaml['results']) {
            def module_args = build_info['invocation']['module_args']
            repos.add([
                url: "https://download.copr.fedorainfracloud.org/results/${module_args['user']}/${module_args['project']}/${module_args['chroot']}",
                dist: convert_to_dist(build_info['chroot'])
            ])
        }
    }

    return repos
}

def convert_to_dist(chroot) {
    if(chroot == 'rhel-9-x86_64') {
        return 'el9'
    } else if(chroot == 'rhel-8-x86_64') {
        return 'el8'
    } else if(chroot == 'rhel-7-x86_64') {
        return 'el7'
    } else {
        return null
    }
}
