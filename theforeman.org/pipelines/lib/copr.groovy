def status_copr_links(repo) {
    if(fileExists('copr_build_info')) {
        def build_info_files = sh(returnStdout: true, script: "ls copr_build_info/", label: "copr build info").trim().split(' ')

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
