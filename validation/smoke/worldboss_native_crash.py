"""Owned disposable JVM crash/restart. No production season, economic or power-loss approval."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import time
import uuid

from rcon import Rcon
from worldboss_rehearsal import load,verify_owner,write_json,crash,launch
from worldboss_native_pilot import run as pilot
from worldboss_journal_evidence import last_complete
from worldboss_process import previous_exited


def damage(fixed):
    return {tuple(row['player']):row['units'] for row in fixed['damage_units']}


def origins(state):
    return {str(row['player']):row['origin'] for row in state['travel']['tickets']}


def marker_count(player,marker):
    return sum(item.get('count',0) for item in player['inventory'] if item.get('id')=='minecraft:diamond'
               and item.get('components',{}).get('minecraft:custom_data',{}).get('worldboss_crash')==marker)


def sdk_snapshot(database):
    """Recovery-only evidence: one consistent, read-only snapshot; never query the private wild secret."""
    connection=sqlite3.connect(database.resolve().as_uri()+'?mode=ro',uri=True,timeout=1)
    try:
        connection.execute('BEGIN')
        profiles={subject:json.loads(body) for subject,body in connection.execute('SELECT pokemon_id,body FROM profiles LIMIT 65')}
        acquisitions=dict(connection.execute("SELECT operation_id,pokemon_id FROM operations WHERE kind='ACQUIRE' LIMIT 65"))
        if len(profiles)>64 or len(acquisitions)>64:raise ValueError('Recovery SDK fixture exceeds evidence envelope')
        return {'profiles':profiles,'acquisitions':acquisitions}
    finally:connection.close()


def run(path,resume=None):
    rig,manifest,props=load(path)
    if manifest.get('recovery_only') is not True or manifest.get('launch_history'):
        raise ValueError('Fresh recovery-only process required')
    if resume is None:
        verify_owner(manifest)
        output=rig/('native-crash-'+str(time.time_ns()));output.mkdir()
        report={'scope':'Process termination/restart of one-Pokemon TEST fixture; no live economy or power-loss signoff',
                'gameplay_sha256':manifest['jar_sha256'],'checks':[],'result':'INCOMPLETE'}
        commands=[];marker='wbcrash_'+uuid.uuid4().hex
    else:
        output=resume.resolve(strict=True)
        if output.parent!=rig or not output.name.startswith('native-crash-') or not previous_exited(manifest):
            raise ValueError('Resume requires the same private evidence directory and an exited original process')
        report=json.loads((output/'report.json').read_text());before=json.loads((output/'before-crash.json').read_text())
        if report['gameplay_sha256']!=manifest['jar_sha256'] or not (output/'omitted-projection.dat').is_file():
            raise ValueError('Resume evidence does not match this recovery set')
        if (rig/'rehearsal-world/data/cobbleraids_world_boss.dat').exists():raise ValueError('Projection omission was not preserved')
        frozen=json.loads((output/'crashed-journal.json').read_text());names=report['players']
        check_frame=last_complete((output/'crashed.journal').read_bytes(),(output/'crashed.head').read_bytes())
        if check_frame['digest']!=frozen['digest'] or check_frame['payload']!=(rig/'logs/recovery-expected.nbt').read_bytes():
            raise ValueError('Expected snapshot differs from retained crash evidence')
        candidates={tag for player in before['players'].values() for tag in player['tags'] if tag.startswith('wbcrash_')}
        if len(candidates)!=1:raise ValueError('Inventory marker is ambiguous')
        marker=candidates.pop();commands=json.loads((output/'commands.json').read_text())
        archive=output/('failed-attempt-'+str(time.time_ns())+'.json');shutil.copy2(output/'report.json',archive)
        report['previous_attempt']=archive.name;report.pop('error',None);report.pop('shutdown_error',None);report['result']='INCOMPLETE'
    bots=[];handles=[]
    write_json(output/'report.json',report)

    def check(name,passed,evidence=None):
        report['checks'].append({'name':name,'passed':bool(passed),'evidence':evidence});write_json(output/'report.json',report)
        if not passed:raise RuntimeError('Recovery check failed: '+name)

    def command(text):
        verify_owner(manifest)
        with Rcon('127.0.0.1',manifest['rcon_port'],props['rcon.password']) as client:response=client.command(text)
        commands.append({'unix':time.time(),'command':text,'response':response});write_json(output/'commands.json',commands)
        print(text+' => '+response,flush=True);return response

    def wait(predicate,seconds):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            if predicate():return True
            time.sleep(1)
        return False

    def snapshot(label):
        response=command('wbperf recovery-snapshot');match=re.search(r'file=(recovery-snapshot-[a-f0-9-]+\.json)',response)
        if match is None:raise RuntimeError('Native snapshot was not written')
        source=rig/'logs'/match.group(1);value=json.loads(source.read_text())
        shutil.copy2(source,output/(label+'.json'));return value

    def disconnect():
        for bot in bots:
            if bot.poll() is None:
                try:bot.stdin.write('QUIT\n');bot.stdin.flush();bot.wait(timeout=5)
                except (OSError,subprocess.TimeoutExpired):bot.kill();bot.wait(timeout=5)
            if bot.stdin:bot.stdin.close()
        bots.clear()
        for handle in handles:handle.close()
        handles.clear()

    def connect(names,label):
        files={}
        for name in names:
            path=output/(label+'-'+name+'.out.log');files[name]=path
            stdout=path.open('wb');stderr=(output/(label+'-'+name+'.err.log')).open('wb');handles.extend([stdout,stderr])
            bots.append(subprocess.Popen(['node',str(Path(__file__).with_name('worldboss_duelbot.js')),name,'127.0.0.1',str(manifest['game_port'])],
                stdin=subprocess.PIPE,stdout=stdout,stderr=stderr,text=True,creationflags=subprocess.CREATE_NO_WINDOW))
        check(label+' clients connected',wait(lambda:all('READY' in p.read_text(errors='replace') for p in files.values()),90))
        return files

    try:
        cache=rig/'rehearsal-world/data/cobbleraids_world_boss.dat'
        if resume is None:
            pilot(rig,require_integration_v2=True,fighters=5,combat_seconds=10,require_startup_warmup=True)
            reports=list(rig.glob('pilot-*/report.json'));check('one completed real-combat setup pilot',len(reports)==1)
            setup=json.loads(reports[0].read_text());check('setup pilot passed',setup['result'].startswith('PASS'))
            names=setup['players'];report['players']=names
            files=connect(names,'before')
            for index,bot in enumerate(bots):
                bot.stdin.write('FIGHT\nSAY /cr worldboss '+('observe' if index==5 else 'fight')+'\n');bot.stdin.flush()
            check('five new battles active before crash',wait(lambda:all('BATTLE_INIT' in files[n].read_text(errors='replace') for n in names[:5]),180))
            for bot in bots:bot.stdin.write('STOP\n');bot.stdin.flush()
            time.sleep(3)  # Drain already submitted native actions; battles remain active, awaiting input.
            command('forceload remove all')  # Recovery must work without persisted vanilla force-load tickets.
            for name in names:
                command('tag '+name+' add '+marker)
                command('give '+name+' minecraft:diamond[minecraft:custom_data={worldboss_crash:"'+marker+'"}] 7')
            command('save-all flush')
            before=snapshot('before-crash');state=before['runtime']
            sdk=sdk_snapshot(rig/'rehearsal-world/cobbleascend/progression.db');write_json(output/'before-crash-sdk.json',sdk)
            check('six canonical SDK acquisitions captured before crash',len(sdk['profiles'])==6 and len(sdk['acquisitions'])==6
                  and set(sdk['acquisitions'].values())==set(sdk['profiles']))
            check('crash target remains ACTIVE with five owned duels',state['event_clock']['phase']=='ACTIVE' and len(before['duels'])==5)
            check('nonzero community progress exists',state['fixed_health']['current_units']<state['fixed_health']['max_units'])
            check('six durable inventory markers and parties captured',all(marker in p['tags'] and marker_count(p,marker)==7 and len(p['party'])==1 for p in before['players'].values()))
            check('original travel locations captured',len(origins(state))==6)
            crash(argparse.Namespace(rig=rig));disconnect()
            journal=rig/'rehearsal-world/data/cobbleraids_world_boss.journal'
            head=rig/'config/cobbleraids/worldboss-recovery/journal.head'
            check('bounded journal evidence',journal.stat().st_size<=80*1024*1024+104)
            frozen=last_complete(journal.read_bytes(),head.read_bytes())
            (rig/'logs/recovery-expected.nbt').write_bytes(frozen.pop('payload'))
            write_json(output/'crashed-journal.json',frozen)
            shutil.copy2(journal,output/'crashed.journal');shutil.copy2(head,output/'crashed.head')
            check('rebuildable projection exists before omission',cache.is_file())
            # Both resolved paths are within this verified disposable rig; only this one file moves.
            destination=output/'omitted-projection.dat'
            if not cache.resolve().is_relative_to(rig) or not destination.resolve().is_relative_to(rig):raise ValueError('Projection evidence escaped private rig')
            cache.rename(destination)
        oldpid=manifest['pid']
        launch(argparse.Namespace(rig=rig,java=Path(manifest['java']),restart=True))
        rig,manifest,props=load(rig)
        def ready():
            try:verify_owner(manifest)
            except (ValueError,subprocess.SubprocessError):return False
            return 'World-boss private simulator ready' in (rig/'logs/latest.log').read_text(errors='replace')
        check('restarted owned server becomes ready',wait(ready,240),{'old_pid':oldpid,'new_pid':manifest['pid']})
        recovered=None
        def arena_ready():
            nonlocal recovered
            recovered=snapshot('recovered-before-login')
            if recovered['runtime']['event_clock']['reason']=='PRESENTATION_FAILURE':raise RuntimeError('Arena presentation failed during native restoration')
            return recovered['showpieces']==1
        check('arena restoration finishes before presentation is inspected',wait(arena_ready,130))
        expected=recovered['expected']['snapshot']['data'];actual=recovered['runtime']
        check('same boss identity and fixed HP recovered',actual['boss']==expected['boss'] and actual['definition']==expected['definition']
              and actual['fixed_health']['max_units']==expected['fixed_health']['max_units'] and actual['fixed_health']['current_units']==expected['fixed_health']['current_units'])
        check('acknowledged contribution units recovered',damage(actual['fixed_health'])==damage(expected['fixed_health']))
        check('opening roster and frozen rules recovered',actual['opening_roster']==expected['opening_roster'] and actual['opening_rules']==expected['opening_rules'])
        check('active budget retained and one recovery charged',actual['event_clock']['remaining_ms']==expected['event_clock']['remaining_ms']
              and actual['event_clock']['recoveries']==expected['event_clock']['recoveries']+1)
        check('recovery is paused or regrouping, never immediate combat',actual['event_clock']['phase'] in ('HELD','REGROUP'))
        check('original travel locations survive projection omission',origins(actual)==origins(expected))
        check('no old duels or duplicate boss entities',not recovered['duels'] and recovered['ownedBossEntities']==0 and recovered['showpieces']==1)
        check('projection rebuilt from authority',cache.is_file())
        check('same recovery identity and journal prefix survived',recovered['installation']==before['installation'] and recovered['sequence']>=frozen['sequence'])
        files=connect(names,'after');joined=snapshot('recovered-with-players')
        sdk=sdk_snapshot(rig/'rehearsal-world/cobbleascend/progression.db');write_json(output/'recovered-sdk.json',sdk)
        expected_sdk=json.loads((output/'before-crash-sdk.json').read_text())
        check('canonical SDK profiles survive without rerolls',sdk['profiles']==expected_sdk['profiles'])
        check('SDK acquisition receipts survive without duplication',sdk['acquisitions']==expected_sdk['acquisitions'])
        check('party UUIDs/species/levels preserved without cloning',all(joined['players'][n]['party']==before['players'][n]['party'] for n in names))
        check('inventory marker counts preserved without duplication',all(marker in joined['players'][n]['tags'] and marker_count(joined['players'][n],marker)==7 for n in names))
        if joined['runtime']['event_clock']['phase']=='HELD':command('cr worldboss control recover')
        for index,bot in enumerate(bots):
            bot.stdin.write('FIGHT\nSAY /cr worldboss '+('observe' if index==5 else 'fight')+'\n');bot.stdin.flush()
        check('five real battles resume after regroup',wait(lambda:all('BATTLE_INIT' in files[n].read_text(errors='replace') for n in names[:5]),180))
        check('all five clients submit new native move responses',wait(lambda:all('SENT ' in files[n].read_text(errors='replace') for n in names[:5]),30))
        resumed=snapshot('resumed')
        check('new encounter IDs replace all pre-crash duels',len(resumed['duels'])==5 and set(resumed['duels']).isdisjoint(before['duels']))
        command('cr worldboss control hold')
        check('post-recovery hold acknowledged',wait(lambda:'World Boss control save: none' in command('cr worldboss status'),30))
        report['result']='PASS'
    except Exception as error:
        report['result']='FAIL';report['error']=str(error);raise
    finally:
        disconnect()
        try:command('stop')
        except (ValueError,OSError,subprocess.SubprocessError) as error:report['shutdown_error']=str(error);report['result']='FAIL'
        write_json(output/'report.json',report);print('Recovery evidence: '+str(output),flush=True)
    if report['result']!='PASS':raise RuntimeError('Recovery rehearsal did not finish cleanly; inspect report.json')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rig',type=Path,required=True)
    parser.add_argument('--resume-after-crash',type=Path,help='Continue preserved pre-restart evidence after a harness interruption')
    args=parser.parse_args();run(args.rig,args.resume_after_crash)
