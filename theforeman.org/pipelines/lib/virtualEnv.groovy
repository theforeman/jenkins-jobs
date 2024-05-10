def virtEnv(path, command, python=null) {
    if(!fileExists("${path}/bin/activate")) {
      if(python) {
        cmd = "python${python}"
      } else {
        cmd = "python3"
      }
        sh "${cmd} -m venv ${path}"
    }

    sh """
    source ${path}/bin/activate
    ${command}
    deactivate
    """
}
