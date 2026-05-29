def candlepin_version = '4.7'
def packaging_branch = 'rpm/4.7'
def candlepin_distros = [
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos9-stream',
        'almalinux9',
    ]
]
