void push_rpms(repo_src, repo_dest, version, distro, keep_old_files = false) {
    push_rpms_direct("${repo_src}/${distro}", "${repo_dest}/${version}/${distro}", !keep_old_files, keep_old_files)
}

void push_rpms_direct(repo_source, repo_dest, overwrite = true, merge = false) {
    sshagent(['repo-sync']) {
        sh "ssh yumrepo@web01.osuosl.theforeman.org ${repo_source} ${repo_dest} ${overwrite} ${merge}"
    }
}

void push_debs_direct(os, repo) {
    sshagent(['freight']) {
        sh "ssh freight@repo-deb01.osuosl.theforeman.org deploy ${os} ${repo}"
    }
}

void push_staging_rpms(repo_src, repo_dest, version, distro, keep_old_files = false) {
    if (repo_dest == 'foreman') {
        destination = "releases/${version}/${distro}"
    } else if (repo_dest == 'katello') {
        destination = "katello/${version}/katello/${distro}"
    } else {
        destination = "${repo_dest}/${version}/${distro}"
    }

    push_rpms_direct("${repo_src}/${distro}", destination, !keep_old_files, keep_old_files)
}

void push_foreman_staging_rpms(repo_type, version, distro) {
    version = version == 'develop' ? 'nightly' : version
    push_staging_rpms("${repo_type}/${version}", repo_type, version, distro)
}

