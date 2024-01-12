def candlepin_version = 'nightly'
def packaging_branch = 'rpm/develop'
def candlepin_distros = [
    'el8',
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos8-stream',
        'centos9-stream'
    ]
]
