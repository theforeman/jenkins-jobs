// action, type, version, os, extra_vars = nil
def pipelineVars(Map args) {
    def box_prefix = "pipe-"
    if (args.action == 'upgrade') {
        box_prefix = "pipe-up-"
    }
    boxes = ["${box_prefix}${args.type}-*${args.version}-${args.os}"]

    extra_vars = [
        'pipeline_version': args.version,
        'pipeline_os': args.os,
        'pipeline_type': args.type,
        'pipeline_action': args.action
    ]

    if (args.extra_vars != null) {
        extra_vars.putAll(args.extra_vars)
    }

    vars = [
      'boxes': boxes,
      'pipeline': args.action,
      'extraVars': extra_vars
    ]
    return vars
}
