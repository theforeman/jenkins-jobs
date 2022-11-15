def setupDuffyClient() {
    sh(label: 'duffy collection install', script: 'ansible-galaxy collection install --collections-path ~/.ansible/collections git+https://github.com/evgeni/evgeni.duffy ansible.posix')

    sh label: 'configure duffy', script: '''
    # we do not want to leak the key to the logs
    set +x
    mkdir -p ~/.config
    echo -e "client:\\n  url: https://duffy.ci.centos.org/api/v1\\n  auth:\\n    name: foreman\\n    key: ${CICO_API_KEY}" > ~/.config/duffy
    '''
}

def provisionDuffy() {
    fix_ansible_config()
    git_clone_jenkins_jobs(target_dir: 'jenkins-jobs')

  	dir('jenkins-jobs/centos.org/ansible') {
        runPlaybook(playbook: 'provision_duffy.yml')
        archiveArtifacts artifacts: 'duffy_inventory'
        archiveArtifacts artifacts: 'ssh_config'
    }
}

def fix_ansible_config() {
    // the yaml stdout callback is gone in ansible-core
    sh(script: "sed -i /stdout_callback/d ansible.cfg", label: 'fix ansible config')
}

def deprovisionDuffy() {
    if (fileExists('jenkins-jobs/centos.org/ansible/duffy_session')) {
        dir('jenkins-jobs/centos.org/ansible') {
            runPlaybook(playbook: 'deprovision_duffy.yml')
      	}
    }
}

def duffy_inventory(relative_dir = '') {
    return relative_dir + 'jenkins-jobs/centos.org/ansible/duffy_inventory'
}

def ssh_config(relative_dir = '') {
    return relative_dir + 'jenkins-jobs/centos.org/ansible/ssh_config'
}

def color_shell(command = '', returnStdout = false) {
    sh(script: "${command}", returnStdout: returnStdout, label: "${command}")
}

def duffy_ssh(command, box_name, relative_dir = '', returnStdout = false) {
    color_shell("ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -F ${ssh_config(relative_dir)} ${box_name} '${command}'", returnStdout)
}

def duffy_scp(file_path, file_dest, box_name, relative_dir = '') {
    color_shell "scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -r -F ${ssh_config(relative_dir)} ${box_name}:${file_path} ${file_dest}"
}

def duffy_scp_in(file_path, file_dest, box_name, relative_dir = '') {
    color_shell("scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -r -F ${ssh_config(relative_dir)} ${file_path} ${box_name}:${file_dest}")
}
