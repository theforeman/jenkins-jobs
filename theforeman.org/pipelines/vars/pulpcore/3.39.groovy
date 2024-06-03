def pulpcore_version = '3.39'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/3.39'
def pipelines = [
    'pulpcore': [
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ]
]
