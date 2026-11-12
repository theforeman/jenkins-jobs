def ghprb_git_checkout(fetch_annex = false) {
    def refspec = '+refs/heads/${ghprbTargetBranch}:refs/remotes/origin/${ghprbTargetBranch} +refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*'
    if (fetch_annex) {
        refspec += ' +refs/heads/synced/git-annex:refs/remotes/origin/synced/git-annex'
    }

    checkout changelog: true, poll: false, scm: [
        $class: 'GitSCM',
        branches: [[name: '${sha1}']],
        doGenerateSubmoduleConfigurations: false,
        extensions: [
            [
                $class: 'CloneOption',
                depth: 2,
                honorRefspec: true,
                noTags: true,
                shallow: true
            ],
            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeTarget: '${ghprbTargetBranch}']]
        ],
        submoduleCfg: [],
        userRemoteConfigs: [
            [credentialsId: 'github-login', refspec: refspec, url: 'https://github.com/${ghprbGhRepository}']
        ]
    ]
}

def git_hash(ref = 'HEAD') {
    return sh(script: "git rev-parse ${ref}", returnStdout: true, label: "git hash").trim()
}

def archive_git_hash(ref = 'HEAD') {
    def hash = git_hash(ref)
    writeFile(file: 'commit', text: hash)
    archiveArtifacts(artifacts: 'commit')
    return hash
}
