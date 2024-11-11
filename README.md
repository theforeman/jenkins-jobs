# Foreman's Jenkins Jobs

This repository contains all the job definitions and supporting code used in Jenkins jobs used by the Foreman project on it's own ci system [ci.theforeman.org](https://ci.theforeman.org) and [ci.centos.org](https://jenkins-foreman.apps.ocp.cloud.ci.centos.org/).

CentOS CI is used to provision VMs for end to end testing of installations.

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
| Pull Request testing    | {git-repo}-{optional-concern}-pr-test                  | katello-pr-test                 | foreman-packaging-rpm-pr-test             |
| Branch testing          | {git-repo}-{git-branch}-test                           | foreman-3.5-stable-test         | smart-proxy-3.5-stable-test               |

## Job configurations

### Testing develop

All repos with an associated job that tests their master/develop branch should have a hook added to the repo to trigger immediate builds.

To set up the hook, an org/repo admin must:

* View the repository settings
* Click *Webhooks*
* Click *Add webhook*
  * Payload URL: https://ci.theforeman.org/github-webhook/
  * Content Type: application/json (default)
  * Secret: add from secret store
  * Just the push event

### Pull request testing

Core Foreman projects have GitHub pull requests tested on our Jenkins instance so it's identical to the way we test the primary development branches themselves.  Simpler and quieter projects (such as installer modules) can use Github Actions instead. There is no obligation for Foreman projects to use Jenkins.

Every project that needs PR testing has at least two Jenkins jobs.  Taking core Foreman as an example:

* Test job for the main development branch (develop or master): [test_develop](https://ci.theforeman.org/job/test_develop/)
* Test job for each PR: [test_develop_pr_core](https://ci.theforeman.org/job/test_develop_pr_core/)

#### Github Pull Request Builder

The GHPRB plugin uses webhooks installed on the repo to trigger a build, then it runs any job configured with the GHPRB trigger and a matching GitHub project set.

The plugin tests the latest commit on the PR branch only, it does not merge the PR with the base branch. The webhook may also trigger multiple jobs, and jobs may use different GitHub commit status names to easily test and report status on different types of tests.

PR jobs should be set up identically to primary branch tests, except for the SCM (which checks out `${sha1}`) and to add the GHPRB trigger (see the `github_pr` macro in JJB).

To set up the hook, an org/repo admin goes to the repository settings, then Webhooks & Services and adds a webhook with these settings:

* Payload URL: `https://ci.theforeman.org/ghprbhook/`
* Content type: `application/json`
* Secret: _redacted_
* Events: _Let me select individual events_, _Pull request_, _Issue comment_

An org admin must then change the org teams:

* Add the repository to the [Bots team](https://github.com/orgs/theforeman/teams/bots/repositories) with **write** access

## Quick reference for maintainers

Current PR test jobs (used on Foreman itself) support these commands:

* `ok to test` - run tests for an unknown user, if the code within the patch is not malicious
* `[test STATUS-NAME]`, e.g. `[test foreman]` to re-run a particular set of tests

# Foreman's Other Tests

Jenkins is not the only place tests are defined and executed.

## GitHub Actions

Several repositories use [GitHub Actions](https://github.com/features/actions) either *instead of* or *together with* Jenkins.
They commonly use our [reusable actions](https://github.com/theforeman/actions).

The definitions of these jobs are in `.github/workflows/` of their respective repositories.

Failed jobs cannot be re-triggered with a comment, only from the GitHub UI which requires maintainer permissions for the repository.

## Packit

Several repositories use [Packit](https://packit.dev) to produce RPMs based on pull requests.

The definitions of these jobs are in `.packit.yaml` of their respective repositories.

Failed jobs can be re-triggered with a `/packit build` comment in the PR.
