def candlepin_version = 'nightly'
def packaging_branch = 'rpm/develop'
def candlepin_distros = [
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos9-stream',
    ]
]
