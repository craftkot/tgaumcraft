#!/usr/bin/env python3
"""Use existing prepared ModDev runs, one real TCP server and graphical client."""
from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, json, os, re, subprocess, time

ROOT=Path('/workspace/tgaumcraft')
DEV=ROOT/'build/moddev'
PORTABLE=Path('/workspace/reference/golem-client-env/root')
JAVA='/workspace/toolchain/jdk-21.0.12.1+1/bin/java'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suffix',default='m59')
    parser.add_argument('--port',type=int,default=25630)
    parser.add_argument('--output',type=Path,default=Path('/workspace/reference/golems-m59-parts-client'))
    args=parser.parse_args()
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    server=ROOT/f'build/golem-parts-server-{args.suffix}'
    client=ROOT/f'build/golem-parts-client-{args.suffix}'
    assert not (server/'world').exists(),f'Fresh world required; use another suffix: {server}'
    server.mkdir(parents=True,exist_ok=True);client.mkdir(parents=True,exist_ok=True)
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text('\n'.join([
        'online-mode=false','enforce-secure-profile=false','server-ip=127.0.0.1',f'server-port={args.port}',
        'level-type=minecraft:flat','generate-structures=false','level-seed=123456','difficulty=peaceful',
        'gamemode=survival','allow-flight=true','max-players=1','view-distance=3','simulation-distance=3',
        'spawn-protection=0','max-tick-time=60000','sync-chunk-writes=true','']) )
    options=ROOT/'build/golem-crop-client/options.txt'
    if options.exists():
        data=options.read_text().splitlines()
        replace={'maxFps':'20','renderDistance':'3','simulationDistance':'3','guiScale':'2','enableVsync':'false','fov':'0.0','pauseOnLostFocus':'false','soundCategory_master':'0.0'}
        result=[]
        for line in data:
            key=line.split(':',1)[0]
            result.append(f'{key}:{replace.pop(key)}' if key in replace else line)
        result.extend(f'{k}:{v}' for k,v in replace.items())
        (client/'options.txt').write_text('\n'.join(result)+'\n')
    artifacts=[p for p in (DEV/'artifacts').glob('neoforge-*.jar') if p.stem.removeprefix('neoforge-').replace('.','').isdigit()]
    assert len(artifacts)==1,'Expected already prepared ModDev artifact'
    env=os.environ.copy();env.pop('THAUMCRAFT_SMOKE',None)
    env.update(DISPLAY=':97',LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
    env['LD_LIBRARY_PATH']=str(PORTABLE/'usr/lib/x86_64-linux-gnu')+(':'+env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
    env['MOD_CLASSES']=os.pathsep.join('thaumcraft%%'+str(ROOT/p) for p in ['build/classes/java/main','build/resources/main'])
    disabled=['golemClientProbe','golemCropClientProbe','golemOwnerClientProbe','golemSealRemoteProbe','golemPlantAdapterProbe','golemSideSupportProbe']
    overrides=[f'-Dthaumcraft.{name}=false' for name in disabled]+['-Dthaumcraft.golemClientScenario=parts','-Dthaumcraft.golemRestartPhase=','-Dthaumcraft.golemRestartScenario=','-Dthaumcraft.golemPartsClientProbe=true']
    def command(kind):
        cp=[str(ROOT/'build/classes/java/main'),str(ROOT/'build/resources/main'),str(artifacts[0]),*(DEV/f'{kind}LegacyClasspath.txt').read_text().splitlines()]
        return [JAVA,'-cp',os.pathsep.join(cp),'@'+str(DEV/f'{kind}RunVmArgs.txt'),*overrides,'@'+str(DEV/f'{kind}RunProgramArgs.txt')]
    program_text=(DEV/'golemClientRunProgramArgs.txt').read_text()
    if '--quickPlayMultiplayer' in program_text:
        program_text=re.sub(r'(?m)^--quickPlayMultiplayer\n[^\n]+',f'--quickPlayMultiplayer\n127.0.0.1:{args.port}',program_text)
    else:
        program_text+=f'\n--quickPlayMultiplayer\n127.0.0.1:{args.port}\n'
    program_file=output/'client-program-args.txt';program_file.write_text(program_text)
    client_command=command('golemClient')
    client_command[-1]='@'+str(program_file)
    client_command+=['--username','M59Parts']
    processes=[];handles=[];records=[];result={};failure=None
    try:
        xvfb_log=(output/'xvfb.log').open('w');handles.append(xvfb_log)
        xvfb=subprocess.Popen([str(PORTABLE/'usr/bin/Xvfb'),':97','-screen','0','1280x800x24','+extension','GLX','+render','-noreset'],env=env,stdout=xvfb_log,stderr=subprocess.STDOUT);processes.append(('xvfb',xvfb))
        for _ in range(100):
            if xvfb.poll() is not None:raise RuntimeError('Xvfb exited before graphical startup')
            if subprocess.run(['xdpyinfo'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:break
            time.sleep(.1)
        else:raise RuntimeError('Xvfb display not ready')
        server_log=(output/'server.log').open('w');handles.append(server_log)
        server_env=env.copy();server_env.pop('DISPLAY',None)
        server_command=command('golemCropServer')+['--nogui']
        srv=subprocess.Popen(server_command,cwd=server,env=server_env,stdout=server_log,stderr=subprocess.STDOUT);processes.append(('server',srv))
        deadline=time.monotonic()+180
        while time.monotonic()<deadline:
            text=(output/'server.log').read_text(errors='replace')
            if 'Done (' in text:break
            if srv.poll() is not None:raise RuntimeError(f'Server exited during startup: {srv.returncode}')
            time.sleep(.25)
        else:raise RuntimeError('Bounded server startup expired')
        client_log=(output/'client.log').open('w');handles.append(client_log)
        cli=subprocess.Popen(client_command,cwd=client,env=env,stdout=client_log,stderr=subprocess.STDOUT);processes.append(('client',cli))
        pending={
          'GOLEM_PARTS_CLIENT_GALLERY_FRAME_OBSERVED':'gallery.png',
          'GOLEM_PARTS_CLIENT_CARGO_FRAME_OBSERVED':'moving-cargo.png',
          'GOLEM_PARTS_CLIENT_FINAL_FRAME_OBSERVED':'deposited.png'}
        deadline=time.monotonic()+420
        while time.monotonic()<deadline:
            ct=(output/'client.log').read_text(errors='replace');st=(output/'server.log').read_text(errors='replace')
            if 'GOLEM_PARTS_CLIENT_ASSERTIONS_FAILED' in ct or 'GOLEM_PARTS_SERVER_ASSERTIONS_FAILED' in st:
                raise RuntimeError('Actual probe assertion failed; see process logs')
            for marker,name in list(pending.items()):
                if marker not in ct:continue
                dest=output/name
                subprocess.run(['import','-window','root',str(dest)],env=env,check=True,timeout=20)
                records.append({'marker':next(line for line in ct.splitlines() if marker in line),'screenshot':name,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'captured_utc':datetime.now(timezone.utc).isoformat()})
                print('RAW_FRAME_CAPTURED='+name,flush=True);del pending[marker]
            if cli.poll() is not None and srv.poll() is not None:break
            time.sleep(.1)
        else:raise RuntimeError('Bounded actual client/server proof expired')
        result.update(server_exit=srv.returncode,client_exit=cli.returncode)
        assert srv.returncode==0 and cli.returncode==0,result
        assert not pending,f'Missing real rendered frames: {pending}'
        assert 'GOLEM_PARTS_SERVER_ASSERTIONS_OK ' in st,'Missing actual server assertions'
        assert 'GOLEM_PARTS_CLIENT_FINAL_ASSERTIONS_OK ' in ct,'Missing actual client assertions'
        assert ct.count('GOLEM_PARTS_CLIENT_PLATE_CLICK_ASSERTIONS_OK ')==3,'Require three actual network crafting results'
        assert st.count('GOLEM_PARTS_SERVER_PLATE_CLICK_ASSERTIONS_OK ')==3,'Require three actual server payment confirmations'
        result['verified']=True
    except BaseException as exc:
        failure=repr(exc);result.update(verified=False,error=failure)
        raise
    finally:
        for name,p in reversed(processes):
            if p.poll() is None:
                p.terminate()
                try:p.wait(timeout=15)
                except subprocess.TimeoutExpired:p.kill();p.wait()
            result[name+'_exit']=p.returncode
        for handle in handles:handle.close()
        result.update(captures=records,server_directory=str(server),client_directory=str(client),observed_utc=datetime.now(timezone.utc).isoformat())
        (output/'results.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,indent=2),flush=True)

if __name__=='__main__':main()
