def pulpcore_version = '{version}'
def pulpcore_distros = ['el7', 'el8']
def packaging_branch = 'rpm/{version}'
def pipelines = [
    'pulpcore': [
        'centos7',
        'centos8-stream'
    ]
]

if (pulpcore_version == '3.18') {{
    pulpcore_distros = ['el8', 'el9']
    pipelines = [
        'pulpcore': [
            'centos8-stream',
            'centos9-stream'
        ]
    ]
}}
