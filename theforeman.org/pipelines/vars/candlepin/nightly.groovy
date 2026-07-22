def candlepin_version = 'nightly'
def packaging_branch = 'rpm/develop'
def candlepin_distros = [
    'el10',
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos10-stream',
        'almalinux10',
        'centos9-stream',
        'almalinux9',
    ]
]
