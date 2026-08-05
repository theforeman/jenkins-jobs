// Name of the JJB job this vars file feeds (pipelines/release/pipelines/konflux-gate.groovy
// + pipelines/lib/konflux_gate.groovy are shared across streams, same as
// katello.groovy is shared across nightly/4.20/4.21 via their own vars files).
// katello.groovy's trigger-konflux-rebuild stage reads this to know which job to
// start — kept here instead of hardcoded in the shared katello.groovy, so a future
// branched stream (e.g. pipelines/vars/konflux/3-19.groovy feeding a
// konflux-gate-3-19-pipeline job) only needs its own vars file, no katello.groovy
// change.
def konflux_gate_job_name = 'konflux-gate-nightly-pipeline'

// Konflux Applications gated by this stream, mapped to the Components bundled in
// each Application's Snapshot (confirmed against the live cluster: `foreman` is a
// single Application whose Snapshot carries both foreman-develop and
// foreman-proxy-develop, not two separate Applications).
def konflux_gate_applications = [
    'candlepin': ['candlepin-develop'],
    'foreman': ['foreman-develop', 'foreman-proxy-develop'],
    'pulp': ['pulp-develop'],
]

// Production ReleasePlan per Application. These do not exist yet — they are created
// by the tenants-config prerequisite MR that splits the current single auto-release
// ReleasePlan (e.g. release-candlepin-develop-nightly, which today auto-releases
// straight to quay.io/foreman/<image>:nightly) into a stage variant (kept
// auto-release, retagged) and this manually-triggered production variant. Update
// these names once that MR lands.
def konflux_gate_release_plans = [
    'candlepin': 'release-candlepin-develop-production',
    'foreman': 'release-foreman-develop-production',
    'pulp': 'release-pulp-develop-production',
]

def snapshot_wait_timeout_minutes = 60
def release_wait_timeout_minutes = 30
