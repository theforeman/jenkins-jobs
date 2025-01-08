# Foreman's Jenkins Jobs

This repository contains all the job definitions and supporting code used in Jenkins jobs used by the Foreman project on it's own ci system [ci.theforeman.org](https://ci.theforeman.org) and [ci.centos.org](https://jenkins-foreman.apps.ocp.cloud.ci.centos.org/).
We use Jenkins for our nightly and release package building.
Those are composed in packaging release pipelines and tested by calling out to CentOS CI to provision VMs for end to end testing of installations.
Then lastly there are a job to deploy our website and deploy our Puppet environment.

## Jenkins Job Builder

[Jenkins Job Builder](https://docs.openstack.org/infra/jenkins-job-builder/) (JJB) is an OpenStack tool for generating Jenkins job definitions (an XML file) from a set of YAML job descriptions, which we store in version control.

A bootstrap job named `jenkins-jobs-update` runs the JJB tool to update the jobs in the live instance whenever a change is merged to this repository.

Useful resources:

* [Job definitions, templates etc.](https://docs.openstack.org/infra/jenkins-job-builder/definition.html)
* [Modules, e.g. SCM, publishers, builders](https://docs.openstack.org/infra/jenkins-job-builder/definition.html#modules)

## Jenkins Job Naming conventions

**Note** Because `centos.org` is a shared environment all jobs are prefixed by `foreman-` to denote they're ours.

| **Name**                | **Convention**                                         | **Example 1**                   | **Example 2**                             |
|-------------------------|--------------------------------------------------------|---------------------------------|-------------------------------------------|
| Nightly Source Builder  | {git-repo}-{git-branch}-source-release                 | foreman-develop-source-release  | hammer-cli-katello-master-source-release  |
| Nightly Package Builder | {git-repo}-{git-branch}-package-release                | foreman-develop-package-release | hammer-cli-katello-master-package-release |
| CI pipeline             | {repository}-{environment}-{optional-concern}-pipeline | foreman-nightly-rpm-pipeline    | foreman-nightly-deb-pipeline              |
| Pull Request testing    | {git-repo}-{optional-concern}-pr-test                  | foreman-packaging-rpm-pr-test   | foreman-packaging-deb-pr-test             |
| Branch testing          | {git-repo}-{git-branch}-test                           | foreman-3.5-stable-test         | smart-proxy-3.5-stable-test               |

# Foreman's Other Tests

Jenkins is not the only place tests are defined and executed.

## GitHub Actions

Repositories use [GitHub Actions](https://github.com/features/actions) and preferably our [reusable actions](https://github.com/theforeman/actions).
The definitions of these jobs are in `.github/workflows/` of their respective repositories.

Failed jobs can be re-triggered from the GitHub UI which requires maintainer permissions for the repository.

## Packit

Several repositories use [Packit](https://packit.dev) to produce RPMs based on pull requests.

The definitions of these jobs are in `.packit.yaml` of their respective repositories.

Failed jobs can be re-triggered with a `/packit build` comment in the PR.
