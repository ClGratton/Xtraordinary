"""
PlatformIO pre-build script: generate the version string in one C++ source.

Results in a version string like:  1.1.0-dev-feat-kosync-xpath-05c6cf8
Keeping the value out of CPPDEFINES prevents a version-only change from
invalidating every object in the PlatformIO environment.
"""

import configparser
import os
import subprocess
import sys


def warn(msg):
    print(f'WARNING [git_branch.py]: {msg}', file=sys.stderr)


def run_git_value(project_dir, args, label):
    try:
        value = subprocess.check_output(
            ['git', *args],
            text=True, stderr=subprocess.PIPE, cwd=project_dir
        ).strip()
        # Strip characters that would break a C string literal
        return ''.join(c for c in value if c not in '"\\')
    except FileNotFoundError:
        warn(f'git not found on PATH; {label} suffix will be "unknown"')
        return 'unknown'
    except subprocess.CalledProcessError as e:
        warn(
            f'git command failed (exit {e.returncode}): '
            f'{e.stderr.strip()}; {label} suffix will be "unknown"'
        )
        return 'unknown'
    except OSError as e:
        warn(
            f'OS error reading git {label}: {e}; '
            f'{label} suffix will be "unknown"'
        )
        return 'unknown'
    except Exception as e:  # pylint: disable=broad-exception-caught
        warn(
            f'Unexpected error reading git {label}: {e}; '
            f'{label} suffix will be "unknown"'
        )
        return 'unknown'


def get_git_branch(project_dir):
    branch = run_git_value(
        project_dir, ['rev-parse', '--abbrev-ref', 'HEAD'], 'branch'
    )
    # Detached HEAD has no branch name.
    if branch == 'HEAD':
        return 'detached'
    return branch


def get_git_short_sha(project_dir):
    return run_git_value(
        project_dir, ['rev-parse', '--short', 'HEAD'], 'short SHA'
    )


def get_base_version(project_dir):
    ini_path = os.path.join(project_dir, 'platformio.ini')
    if not os.path.isfile(ini_path):
        warn(f'platformio.ini not found at {ini_path}; base version will be "0.0.0"')
        return '0.0.0'
    config = configparser.ConfigParser()
    config.read(ini_path)
    if not config.has_option('crosspoint', 'version'):
        warn('No [crosspoint] version in platformio.ini; base version will be "0.0.0"')
        return '0.0.0'
    return config.get('crosspoint', 'version')


def resolve_version(env):
    project_dir = env['PROJECT_DIR']
    base_version = get_base_version(project_dir)
    environment = env['PIOENV']
    if environment == 'x3_companion':
        return 'xtraordinary-dev'
    if environment == 'x3_companion_release':
        version = os.environ.get('XTRAORDINARY_VERSION', '').strip()
        if not version:
            raise RuntimeError('XTRAORDINARY_VERSION is required for x3_companion_release')
        return version
    if environment == 'gh_release':
        return base_version
    if environment == 'gh_release_rc':
        rc_hash = os.environ.get('CROSSPOINT_RC_HASH', '').strip()
        if not rc_hash:
            raise RuntimeError('CROSSPOINT_RC_HASH is required for gh_release_rc')
        return f'{base_version}-rc+{rc_hash}'
    if environment == 'slim':
        return f'{base_version}-slim'

    branch = get_git_branch(project_dir)
    short_sha = get_git_short_sha(project_dir)
    return f'{base_version}-dev-{branch}-{short_sha}'


def cpp_string(value):
    return value.replace('\\', '\\\\').replace('"', '\\"')


def generate_version_source(env):
    version_string = resolve_version(env)
    output_path = os.path.join(
        env['PROJECT_DIR'], 'lib', 'BuildVersion', 'BuildVersion.cpp'
    )
    content = (
        '#include "BuildVersion.h"\n\n'
        f'const char CROSSPOINT_VERSION[] = "{cpp_string(version_string)}";\n'
    )
    existing = None
    try:
        with open(output_path, 'r', encoding='utf-8') as source:
            existing = source.read()
    except FileNotFoundError:
        pass
    if existing != content:
        with open(output_path, 'w', encoding='utf-8', newline='\n') as source:
            source.write(content)

    print(f'CrossPoint build version: {version_string}')


# PlatformIO/SCons entry point — Import and env are SCons builtins injected at runtime.
# When run directly with Python (e.g. for validation), a lightweight fake env is used
# so the git/version logic can be exercised without a full build.
try:
    Import('env')           # noqa: F821  # type: ignore[name-defined]
    generate_version_source(env)  # noqa: F821  # type: ignore[name-defined]
except NameError:
    class _Env(dict):
        pass

    _project_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    generate_version_source(_Env({'PIOENV': 'default', 'PROJECT_DIR': _project_dir}))
