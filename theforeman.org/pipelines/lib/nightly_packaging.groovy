// The one place that defines how gettext catalogs are compiled before 'gem build'
def catalog_compile_command() {
    return 'make -C locale all-mo'
}

// Gemspecs package locale/ by filesystem glob, so the .mo files must exist on
// disk before 'gem build'. Projects without a locale/Makefile all-mo target are
// left alone; if compilation is attempted, a failure or an empty result fails the build.
def compile_catalogs(ruby_version) {
    if (fileExists('locale/Makefile') && sh(script: "grep -q '^all-mo[[:space:]]*:' locale/Makefile", returnStatus: true) == 0) {
        withRuby(ruby_version, catalog_compile_command())
        withRuby(ruby_version, '''test -z "$(find locale -name '*.po' -print -quit)" || test -n "$(find locale -name '*.mo' -print -quit)" || { echo 'ERROR: catalog compilation produced no .mo files' >&2; exit 1; }''')
    }
}

def generate_sourcefiles(args) {
    def sourcefile_paths = []
    def project_name = args.project_name
    def ruby_version = args.ruby_version ?: '3.0.4'
    def source_type = args.source_type

    dir(project_name) {

        echo source_type
        if (source_type == 'gem') {
            compile_catalogs(ruby_version)
            withRuby(ruby_version, "gem build *.gemspec")
            sourcefiles = list_files('./').findAll { "${it}".endsWith('.gem') }
            archiveArtifacts(artifacts: sourcefiles.join(','))
            sourcefile_paths = sourcefiles.collect { "${pwd()}/${it}" }
        } else {
            bundleInstall(ruby_version)
            bundleExec(ruby_version, "rake pkg:generate_source")
            archiveArtifacts(artifacts: 'pkg/*')
            sourcefile_paths = list_files('pkg/').collect {
                "${pwd()}/pkg/${it}"
            }
        }
    }

    return sourcefile_paths
}
