def pulpcore_version = '3.22'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/3.22'
def pipelines = [
    'pulpcore': [
        'centos8-stream',
        'centos9-stream'
    ]
]
def stage_source = 'koji'
