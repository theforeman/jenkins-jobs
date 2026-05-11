def pulpcore_version = '3.105'
def pulpcore_distros = ['el9']
def packaging_branch = 'rpm/3.105'
def pipelines = [
    'pulpcore': [
        'centos9-stream',
        'almalinux9',
    ]
]
