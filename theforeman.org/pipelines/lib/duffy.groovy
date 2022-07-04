def provision() {
    fix_ansible_config()
    git_clone_jenkins_jobs(target_dir: 'jenkins-jobs')

  	dir('jenkins-jobs/centos.org/ansible') {
        runPlaybook(playbook: 'provision.yml')
        archiveArtifacts artifacts: 'cico_data.json'
        archiveArtifacts artifacts: 'cico_inventory'
        archiveArtifacts artifacts: 'ssh_config'
    }
}

def fix_ansible_config() {
    sh(script: "sed -i s/yaml/debug/g ansible.cfg", label: 'fix ansible config')
}

def deprovision() {
    if (fileExists('jenkins-jobs/centos.org/ansible/cico_data.json')) {
        dir('jenkins-jobs/centos.org/ansible') {
            runPlaybook(playbook: 'deprovision.yml')
      	}
    }
}

def cico_inventory(relative_dir = '') {
    return relative_dir + 'jenkins-jobs/centos.org/ansible/cico_inventory'
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
