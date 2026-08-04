def candlepin_version = 'nightly-el10'
def packaging_branch = 'rpm/develop'
def candlepin_distros = [
    'el10'
]
def pipelines = [
    'candlepin': [
        'centos10-stream',
        'almalinux10',
    ]
]
