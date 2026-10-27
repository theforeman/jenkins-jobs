#!/usr/bin/python

import json
import time
from urllib.parse import quote, urlencode

from ansible.module_utils.basic import AnsibleModule
from ansible.module_utils.urls import fetch_url


class GateCleanup:
    def __init__(self, module):
        self.module = module
        self.base = module.params['url'].rstrip('/')
        self.job = module.params['job']
        self.job_path = '/job/' + quote(self.job, safe='')
        self.request_id = module.params['request_id']
        self.changed = False

    def request(self, path, method='GET', statuses=(200,)):
        response, info = fetch_url(self.module, self.base + path, method=method, timeout=10)
        status = info['status']
        if status not in statuses:
            self.module.fail_json(msg='Jenkins %s %s returned %s: %s' % (
                method, path, status, info.get('msg', '')), changed=self.changed)
        try:
            data = json.loads(response.read()) if method == 'GET' and response else {}
        finally:
            if response:
                response.close()
        return status, data

    def matches(self, item):
        return any(
            parameter.get('name') == 'GATE_REQUEST_ID' and parameter.get('value') == self.request_id
            for action in (item.get('actions') or []) if action
            for parameter in (action.get('parameters') or [])
        )

    def run(self):
        if not self.request_id:
            self.module.fail_json(msg='A nonempty gate request ID is required for cancellation')
        deadline = time.monotonic() + self.module.params['timeout']
        discovery_deadline = time.monotonic() + self.module.params['discovery_timeout']
        seen = False
        cancelled = set()
        cancel_requested = set()
        stopped = set()
        queue_query = urlencode({'tree': 'items[id,task[name],actions[parameters[name,value]]]'})
        build_query = urlencode({'tree': 'builds[number,building,result,actions[parameters[name,value]]]'})

        while time.monotonic() < deadline:
            _, queue = self.request('/queue/api/json?' + queue_query)
            # Query builds after the queue so a queued-to-running transition can
            # be found in this pass, or in the next if the POST is still in flight.
            _, job = self.request(self.job_path + '/api/json?' + build_query)
            queued = [item for item in queue.get('items', [])
                      if item.get('task', {}).get('name') == self.job and self.matches(item)]
            builds = [build for build in job.get('builds', []) if self.matches(build)]
            seen = seen or bool(queued or builds)

            if not queued and builds and all(not build['building'] for build in builds):
                return self.result(builds, cancelled)
            if not queued and not builds and cancelled:
                return self.result([], cancelled)

            for item in queued:
                queue_id = item['id']
                if queue_id not in cancel_requested:
                    status, _ = self.request('/queue/cancelItem?id=%s' % queue_id,
                                             method='POST', statuses=(204, 404))
                    cancel_requested.add(queue_id)
                    if status == 204:
                        cancelled.add(queue_id)
                        self.changed = True
                    # 404 means it may have started. Do not declare cleanup
                    # complete until the correlated build is found and finishes.

            for build in builds:
                number = build['number']
                if build['building'] and number not in stopped:
                    status, _ = self.request(self.job_path + '/%s/stop' % number,
                                             method='POST', statuses=(200, 302, 404))
                    stopped.add(number)
                    self.changed = self.changed or status != 404
                    # Stop once: repeated interrupts could kill the child's
                    # finally/post actions while they are retiring Duffy.

            if not seen and time.monotonic() >= discovery_deadline:
                self.module.fail_json(
                    msg='No child found for gate request %s; dispatch/cleanup could not be confirmed' % self.request_id,
                    changed=self.changed)
            time.sleep(self.module.params['poll_interval'])

        self.module.fail_json(msg='Timed out waiting for gate request %s to finish cleanup' % self.request_id,
                              changed=self.changed)

    def result(self, builds, cancelled):
        return dict(changed=self.changed, cancelled_queue_ids=sorted(cancelled), builds=[
            dict(url=self.base + self.job_path + '/%s/' % build['number'], result=build['result'])
            for build in builds
        ])


def main():
    module = AnsibleModule(argument_spec=dict(
        url=dict(type='str', required=True),
        url_username=dict(type='str', required=True),
        url_password=dict(type='str', required=True, no_log=True),
        force_basic_auth=dict(type='bool', default=True),
        job=dict(type='str', required=True),
        request_id=dict(type='str', required=True),
        timeout=dict(type='int', default=510),
        discovery_timeout=dict(type='int', default=30),
        poll_interval=dict(type='float', default=5),
    ))
    module.exit_json(**GateCleanup(module).run())


if __name__ == '__main__':
    main()
