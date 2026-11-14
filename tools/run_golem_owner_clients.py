#!/usr/bin/env python3
"""Run the two real M10 owner-test clients after prepareGolemClientRun.

The dedicated golemOwnerServer and two X displays must already be running.
All assertions live in the clients/server, not in this process launcher.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--display-a', default=':97')
    parser.add_argument('--display-b', default=':98')
    parser.add_argument('--output-directory', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    dev = root / 'build/moddev'
    artifacts = [p for p in (dev / 'artifacts').glob('neoforge-*.jar')
                 if p.stem.removeprefix('neoforge-').replace('.', '').isdigit()]
    if len(artifacts) != 1:
        parser.error('Prepare the owner client run first; expected one NeoForge artifact')
    vm = dev / 'golemClientRunVmArgs.txt'
    if '-Dthaumcraft.golemOwnerClientProbe=true' not in vm.read_text():
        parser.error('Prepare with -PgolemClientScenario=owners')
    classpath = [str(root / 'build/classes/java/main'), str(root / 'build/resources/main'),
                 str(artifacts[0]), *(dev / 'golemClientLegacyClasspath.txt').read_text().splitlines()]
    args.output_directory.mkdir(parents=True, exist_ok=True)
    processes = []
    logs = []
    try:
        for suffix, username, display in [('a', 'M53Alice', args.display_a), ('b', 'M53Bob', args.display_b)]:
            directory = root / f'build/golem-owner-client-{suffix}'
            directory.mkdir(parents=True, exist_ok=True)
            environment = os.environ.copy()
            environment.pop('THAUMCRAFT_SMOKE', None)
            environment.update(DISPLAY=display, LIBGL_ALWAYS_SOFTWARE='1', ALSOFT_DRIVERS='null')
            environment['MOD_CLASSES'] = os.pathsep.join('thaumcraft%%' + str(root / p)
                for p in ['build/classes/java/main', 'build/resources/main'])
            log = (args.output_directory / f'{username}.log').open('w')
            logs.append(log)
            command = [args.java, '-cp', os.pathsep.join(classpath), '@' + str(vm),
                       '@' + str(dev / 'golemClientRunProgramArgs.txt'), '--username', username]
            processes.append((username, subprocess.Popen(command, cwd=directory, env=environment,
                                                         stdout=log, stderr=subprocess.STDOUT)))
        status = 0
        for username, process in processes:
            code = process.wait(timeout=600)
            print(f'{username}_PROCESS_EXIT={code}', flush=True)
            status |= code != 0
        return int(status)
    finally:
        for _, process in processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        for log in logs:
            log.close()


if __name__ == '__main__':
    sys.exit(main())
