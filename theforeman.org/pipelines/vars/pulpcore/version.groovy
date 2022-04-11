def pulpcore_version = '{version}'
def pulpcore_distros = ['el8']
def packaging_branch = 'rpm/{version}'
def pipelines = [
    'pulpcore': [
        'centos8-stream'
    ]
]
