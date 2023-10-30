def pulpcore_version = '3.18'
def pulpcore_distros = ['el7', 'el8', 'el9']
def packaging_branch = 'rpm/3.18'
def pipelines = [
    'pulpcore': [
        'centos7',
        'centos8-stream',
        'centos9-stream'
    ]
]
def stage_source = 'koji'
