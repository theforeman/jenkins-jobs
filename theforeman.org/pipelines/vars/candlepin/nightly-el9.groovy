def candlepin_version = 'nightly-el9'
def packaging_branch = 'rpm/4.8'
def candlepin_distros = [
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos9-stream',
        'almalinux9',
    ]
]
