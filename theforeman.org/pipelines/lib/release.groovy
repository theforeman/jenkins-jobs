void push_foreman_rpms(repo_type, version, distro) {
    version = version == 'develop' ? 'nightly' : version
    push_rpms("foreman-${repo_type}-${version}", repo_type, version, distro)
}

void push_rpms(repo_src, repo_dest, version, distro, keep_old_files = false, staging = false) {
    push_rpms_direct("${repo_src}/${distro}", "${repo_dest}/${version}/${distro}", !keep_old_files, keep_old_files, staging)
}

void push_rpms_direct(repo_source, repo_dest, overwrite = true, merge = false, staging = false) {
    sshagent(['repo-sync']) {
        sh "ssh yumrepo@web01.osuosl.theforeman.org ${repo_source} ${repo_dest} ${overwrite} ${merge} ${staging}"
    }
}

void push_rpms_katello(version) {
    sshagent(['katello-fedorapeople']) {
        sh "ssh katelloproject@fedorapeople.org 'cd /project/katello/bin && sh rsync-repos-from-koji ${version}'"
    }
}

void push_debs_direct(os, repo) {
    sshagent(['freight']) {
        sh "ssh freight@web01.osuosl.theforeman.org deploy ${os} ${repo}"
    }
}

void push_pulpcore_rpms(version, distro) {
    push_rpms("pulpcore-${version}", "pulpcore", version, distro, true)
}

void push_katello_rpms(version, distro) {
    keep_old = version != 'nightly'
    push_rpms_direct("katello-${version}/katello/${distro}", "katello/${version}/katello/${distro}", !keep_old, keep_old)
    push_rpms_direct("katello-${version}/candlepin/${distro}", "katello/${version}/candlepin/${distro}", !keep_old, keep_old)
}

void mash(collection, version) {
    sshagent(['mash']) {
        sh "ssh -o 'BatchMode yes' root@koji.katello.org collection-mash-split.py ${collection} ${version}"
    }
}

void push_staging_rpms(repo_src, repo_dest, version, distro, keep_old_files = false) {
    if (repo_dest == 'foreman') {
        destination = "releases/${version}/${distro}"
    } else if (repo_dest == 'katello') {
        destination = "katello/${version}/katello/${distro}"
    } else if (repo_dest == 'candlepin') {
        destination = "katello/${version}/candlepin/${distro}"
    } else {
        destination = "${repo_dest}/${version}/${distro}"
    }

    push_rpms_direct("${repo_src}/${distro}", destination, !keep_old_files, keep_old_files, true)
}

void push_foreman_staging_rpms(repo_type, version, distro) {
    version = version == 'develop' ? 'nightly' : version
    push_staging_rpms("${repo_type}/${version}", repo_type, version, distro)
}

