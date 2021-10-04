def update_build_description_from_packages(packages_to_build) {
    build_description = packages_to_build
    if (build_description instanceof String[]) {
        build_description = build_description.join(' ')
    }
    currentBuild.description = build_description
}

def diff_filter(range, filter, path) {
    return sh(returnStdout: true, script: "git diff ${range} --name-only --diff-filter=${filter} -- '${path}'").trim()
}

def find_added_or_changed_files(diff_range, path) {
    return diff_filter(diff_range, 'ACMRTUXB', path)
}

def find_deleted_files(diff_range, path) {
    return diff_filter(diff_range, 'D', path)
}

def find_changed_files(diff_range, path) {
    return diff_filter(diff_range, 'M', path)
}

def find_changed_packages(diff_range) {
    def changed_packages = find_added_or_changed_files(diff_range, 'packages/**.spec')

    if (changed_packages) {
        changed_packages = sh(returnStdout: true, script: "echo '${changed_packages}' | xargs dirname | xargs -n1 basename |sort -u").trim()
    } else {
        changed_packages = ''
    }

    return changed_packages.split()
}

def find_changed_debs(diff_range) {
    def changed_debs = []

    def changed_dependencies = find_added_or_changed_files(diff_range, 'dependencies/*/*/changelog').split()
    def changed_plugins = find_added_or_changed_files(diff_range, 'plugins/*/changelog').split()
    def changed_core = find_added_or_changed_files(diff_range, 'debian/*/*/changelog').split()

    for(core in changed_core) {
        (folder, os, project) = core.split('/')

        changed_debs.add([
            type: 'core',
            name: project,
            path: "${folder}/${os}/${project}",
            operating_system: os
        ])
    }

    for(dependency in changed_dependencies) {
        (folder, os, project) = dependency.split('/')

        changed_debs.add([
            type: 'dependency',
            name: project,
            path: "${folder}/${os}/${project}",
            operating_system: os
        ])
    }

    for(plugin in changed_plugins) {
        (folder, project) = plugin.split('/')

        changed_debs.add([
            type: 'plugin',
            name: project,
            path: "${folder}/${project}",
            operating_system: 'bionic'
        ])
    }

    return changed_debs
}

def query_rpmspec(specfile, queryformat) {
    result = sh(returnStdout: true, script: "rpmspec -q --srpm --undefine=dist --undefine=foremandist --queryformat=${queryformat} ${specfile}").trim()
    return result
}

def repoclosure(repo, dist, version) {
    version = version == 'nightly' ? 'develop' : version
    git_repo = repo == 'pulpcore' ? 'pulpcore-packaging' : 'foreman-packaging'
    ws(dist) {
        dir('packaging') {
            git url: "https://github.com/theforeman/${git_repo}", branch: "rpm/${version}", poll: false
            setup_obal()
            obal(
                action: 'repoclosure',
                packages: "${repo}-repoclosure-${dist}"
            )
        }
    }
}

def repoclosures(repo, releases, version) {
    def closures = [:]

    releases.each { release ->
        closures[release] = { repoclosure(repo, release, version) }
    }

    return closures
}

def debian_package_version(changelog_path) {
    return sh(returnStdout: true, script: "head -n1 ${changelog_path} |awk '{print \$2}'|sed 's/(//;s/)//'|cut -f1 -d-|cut -d: -f2").trim()
}

def build_deb_package_steps(packages_to_build, version, repoowner = 'theforeman', pull_request = false) {
    def steps = [
        setup_sources: [:],
        executes: [:],
        rsync_packages: [:]
    ]

    def repos = [:]

    for(int i = 0; i < packages_to_build.size(); i++) {
        def pkg_to_build = packages_to_build[i]
        echo "${pkg_to_build}"

        def project = pkg_to_build['name']
        def os = pkg_to_build['operating_system']
        def type = pkg_to_build['type']
        def deb_path = deb_build_dir(type, project, os)
        def execute_pbuilder_dir

        steps.setup_sources["${os}-${project}"] = {
            execute_pbuilder_dir = setup_sources_deb(type, project, os, version, repoowner, pull_request)
        }

        steps.executes["${os}-${project}"] = {
            execute_pbuilder(execute_pbuilder_dir, os, version)
        }

        if (type == 'plugin' && !pull_request) {
            suite = 'plugins'
            component = version
            release_type = 'release'
            echo "plugin build without PR: uploading directly to deb/${suite}/${component}"
        } else if (type == 'plugin' && pull_request) {
            suite = 'plugins'
            component = repoowner
            release_type = 'stage'
            echo "scratch build: uploading to stagingdeb/${suite}/${component}"
        } else {
            suite = os
            component = "${repoowner}-${version}"
            release_type = 'stage'
            echo "scratch build: uploading to stagingdeb/${suite}/${component}"
        }

        if (!repos.containsKey("${suite}-${component}")) {
            repos["${suite}-${component}"] = [suite: suite, component: component, deb_paths: [], type: release_type]
        }
        repos["${suite}-${component}"].deb_paths.add("${deb_path}/*deb")
    }

    repos.each { key, value ->
        if (value.type == 'stage') {
            steps.rsync_packages[key] = {
                rsync_to_debian_stage(value.suite, value.component, value.deb_paths)
            }
        } else {
            steps.rsync_packages[key] = {
                rsync_to_debian_release(value.suite, value.component, value.deb_paths)
            }
        }
    }

    return steps
}

def deb_build_dir(type, project, os = null) {
    def build_dir

    if (type == 'core') {
        build_dir = "debian/${os}/build-${project}"
    } else if (type == 'dependency') {
        build_dir = "dependencies/${os}/build-${project}"
    } else if (type == 'plugin') {
        build_dir = "plugins/build-${project}"
    } else {
        echo "Unsupported type specified: ${type}"
        exit 1
    }

    return build_dir
}

def setup_sources_deb(type, project, os, version, repoowner, pull_request) {

    if (type == 'core') {
        setup_sources_core(project, os, version, repoowner, pull_request)
    } else if (type == 'dependency') {
        setup_sources_dependency(project, os, version, repoowner, pull_request)
    } else if (type == 'plugin') {
        setup_sources_plugin(project, os, version, repoowner, pull_request)
    } else {
        echo "Unsupported type specified: ${type}"
        exit 1
    }

}

def setup_sources_core(project, os, version, repoowner, pull_request = false) {
    def package_version = debian_package_version("debian/${os}/${project}/changelog")
    def build_dir = deb_build_dir('core', project, os)

    package_version = package_version.replaceAll('~rc', '-rc')
    last_commit = package_version

    dir(build_dir) {
        if (version == 'nightly') {
            def url_base = 'https://ci.theforeman.org'
            def job_name = nightly_jenkins_job
            def job_id = nightly_jenkins_job_id
            def job_url = "${url_base}/job/${job_name}/${job_id}"

            sh """
              wget "${job_url}/artifact/*zip*/archive.zip"
              unzip archive.zip
              mv archive/pkg/*bz2 ${project}_${package_version}.orig.tar.bz2

              # Set this in case we need it
            """

            last_commit = sh(returnStdout: true, script: "curl \"${job_url}/api/json\" | jq -r '.actions[].lastBuiltRevision.SHA1 | values'").trim()
        } else {
            sh """
              # Download sources
              wget https://downloads.theforeman.org/${project}/${project}-${package_version}.tar.bz2 https://downloads.theforeman.org/${project}/${project}-${package_version}.tar.bz2.sig
              mv ${project}-${package_version}.tar.bz2 ${project}_${package_version}.orig.tar.bz2
            """
        }

        sh """
            # Unpack
            tar xf ${project}_${package_version}.orig.tar.bz2
            if [[ -d ${project}-${package_version}-develop ]] ; then
              mv ${project}-${package_version}-develop ${project}-${package_version}
            fi

            # Bring in the debian packaging files
            cp -r ../${project} ./${project}-${package_version}/debian
        """

        dir("${project}-${package_version}") {
            if (pull_request) {
                add_debian_changelog(os, package_version, repoowner, last_commit)
                sh "mv ../${project}_${package_version}.orig.tar.bz2 ../${project}_9999.orig.tar.bz2"
            }
        }
    }

    return "${build_dir}/${project}-${package_version}"
}

def setup_sources_dependency(project, os, version, repoowner, pull_request = false) {
    def package_version = debian_package_version("dependencies/${os}/${project}/changelog")
    def build_dir = deb_build_dir('dependency', project, os)
    def package_dir

    dir(build_dir) {
        sh """
            BUILD_TYPE=gem
            # Import variables from the project, allowing it to override behaviour
            if [ -e ../${project}/build_vars.sh ]; then
              . ../${project}/build_vars.sh
            fi

            if [[ \$BUILD_TYPE == "python" ]]; then
              pip download --no-deps --no-binary=:all: "${project}==${package_version}"
              mkdir "${project}-${package_version}"
              tar -x -C "${project}-${package_version}" --strip-components=1 -f "${project}-${package_version}.tar.gz"
              mv "${project}-${package_version}.tar.gz" "${project}_${package_version}.orig.tar.gz"
              cp -r ../${project} "${project}-${package_version}/debian"
            elif [[ \$BUILD_TYPE == "gem" ]]; then
              gem fetch ${project} -v "=${package_version}"
              ../../gem2deb ${project}-${package_version}.gem --debian-subdir ../${project} --only-source-dir
            else
              echo "Unsupported build type: \${BUILD_TYPE}"
              exit 1
            fi
        """

        package_dir = sh(returnStdout: true, script: "find -maxdepth 1 -type d -not -name '.'").trim()
    }

    dir("${build_dir}/${package_dir}") {
        if (pull_request) {
            last_commit = git_hash()
            add_debian_changelog(os, package_version, repoowner, last_commit)
        }
    }

    return "${build_dir}/${package_dir}"
}

def setup_sources_plugin(project, os, version, repoowner, pull_request = false) {
    def package_version
    def build_dir = deb_build_dir('plugin', project)
    def package_dir

    dir(build_dir) {
        if (project.contains('smart_proxy')) {
            package_version = debian_package_version("../${project}/changelog")

            sh """
                gem fetch ${project} -v "=${package_version}"
                ../../dependencies/gem2deb ${project}-${package_version}.gem --debian-subdir ../${project} --only-source-dir
            """

            package_dir = sh(returnStdout: true, script: "find -maxdepth 1 -type d -not -name '.'").trim()
        } else {
            package_version = debian_package_version("../${project}/debian/changelog")

            sh """
                BUILD_TYPE=gem
                # Import variables from the project, allowing it to override behaviour
                if [ -e ../${project}/build_vars.sh ]; then
                  . ../${project}/build_vars.sh
                fi

                if [ "\$BUILD_TYPE" = "gem" ]; then
                    cp -r ../${project} ./
                    cd ${project}
                    ../../download_gems
                elif [ "\$BUILD_TYPE" = "ansible-collection" ]; then
                    COLLECTION=${project.replace('ansible-collection-', '')}
                    wget "https://galaxy.ansible.com/download/\${COLLECTION}-${package_version}.tar.gz"
                    mv "\${COLLECTION}-${package_version}.tar.gz" "${project}_${package_version}.orig.tar.gz"
                    cp -r ../${project} ./
                    tar -x -C "${project}" -f "${project}_${package_version}.orig.tar.gz"
                else
                    echo "Unsupported build type: \${BUILD_TYPE}"
                    exit 1
                fi
            """

            package_dir = project
        }
    }

    dir("${build_dir}/${package_dir}") {
        if (pull_request) {
            last_commit = git_hash()
            add_debian_changelog('plugins', package_version, repoowner, last_commit)
        }
    }

    return "${build_dir}/${package_dir}"
}

def add_debian_changelog(suite, package_version, repoowner, last_commit) {
    sh "\$(git rev-parse --show-toplevel)/scripts/changelog.rb --author '${repoowner} <no-reply@theforeman.org>' --version '9999-${package_version}-${suite}+scratchbuild${BUILD_TIMESTAMP}' --message 'Automatically built package based on the state of foreman-packaging at commit ${last_commit}' debian/changelog"
}

def execute_pbuilder(build_dir, os, version) {
    dir(build_dir) {
        sh "sudo FOREMAN_VERSION=${version} pdebuild-${os}64"
        sh "sudo chown -R jenkins:jenkins ../"
    }
}

def rsync_to_debian_stage(suite, component, deb_paths) {
    def user = 'freightstage'
    def ssh_key = '/home/jenkins/workspace/staging_key/rsync_freightstage_key'

    rsync_debian(user, ssh_key, suite, component, deb_paths)
}

def rsync_to_debian_release(suite, component_deb_paths) {
    def user = 'freight'
    def ssh_key = '/home/jenkins/workspace/staging_key/rsync_freight_key'

    rsync_debian(user, ssh_key, suite, component, deb_paths)
}

def rsync_debian(user, ssh_key, suite, component, deb_paths) {
    def hosts = ["web01.osuosl.theforeman.org"]

    for(host in hosts) {
        def target_path = "${user}@${host}:rsync_cache/${suite}/${component}/"

        sh """
            export RSYNC_RSH="ssh -i ${ssh_key}"
            /usr/bin/rsync -avPx ${deb_paths.join(' ')} ${target_path}
        """
    }
}
