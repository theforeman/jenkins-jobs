def generate_sourcefiles(args) {
    def sourcefile_paths = []
    def project_name = args.project_name
    def ruby_version = args.ruby_version ?: '3.0.4'
    def source_type = args.source_type

    dir(project_name) {

        echo source_type
        if (source_type == 'gem') {
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
