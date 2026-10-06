#!/usr/bin/env python3
"""Real JNI conversation proof using fresh app-exported context/grammars, never owner data.
Export fixtures with YUKI_PROOF_DIR during Android unit checks, then prepare_contracts.py.
This is host execution; Android binding/resource admission and Galaxy acceptance remain separate.
"""
import argparse, base64, hashlib, json, re, subprocess, time
from pathlib import Path

p=argparse.ArgumentParser()
for name in ('java','classes','library','model','contracts','results'): p.add_argument('--'+name,required=True)
a=p.parse_args();contracts=Path(a.contracts);results=Path(a.results);results.mkdir(parents=True,exist_ok=True)
outputs={};measurements={};fixture_hashes={}
fixtures=results/"fixtures";fixtures.mkdir(exist_ok=True)
def run(role,enum,user,deadline):
    grammar=contracts/(role+'-full.gbnf');system=contracts/(role+'-system.txt')
    for f in (user,grammar,system):
        fixture_hashes[f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
        (fixtures/f.name).write_bytes(f.read_bytes())
    command=[a.java,'--add-opens','java.base/java.io=ALL-UNNAMED','-Djava.library.path='+a.library,
             '-Dyuki.role='+role,'-Dyuki.production=true','-Dyuki.output=64','-Dyuki.deadline='+str(deadline),
             '-Dyuki.system='+str(system),'-Dyuki.user='+str(user),'-Dyuki.grammar='+str(grammar),
             '-cp',a.classes,'com.mavyy.localyuki.inference.NativeOrgan',a.model]
    # Language-only avoids the harness's additional embedding proof in this conversation deadline.
    process=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=deadline/1000+20)
    (results/(role+'.log')).write_text(process.stdout)
    if process.returncode:raise RuntimeError(role+' JNI failed; inspect '+str(results/(role+'.log')))
    line=next(line for line in process.stdout.splitlines() if line.startswith('GENERATION '))
    output=base64.b64decode(line.split(' outputBase64=')[1]).decode();decoded=json.loads(output)
    metrics=json.loads(re.search(r'metrics=(\[[^]]+\])',line).group(1))
    assert 0<metrics[4]<=64 and metrics[5]==0 and 'UNLOAD completed' in process.stdout
    outputs[enum]=output;measurements[enum]={'startupMs':int(re.search(r'startupMs=(\d+)',line).group(1)),
        'inferenceMs':int(re.search(r'inferenceMs=(\d+)',line).group(1)),
        'preparationMs':metrics[6],'generationMs':metrics[7],'generatedTokens':metrics[4],'deadlineMs':deadline}
    print(enum,json.dumps(measurements[enum]),flush=True);return decoded

started=time.monotonic()
one=run('one','SYSTEM_ONE',contracts/'one-full-user.json',30000)
assert one['route']=='RESPOND' and one['confidence']>=60 and len(one['meaning'])==1
uncertainty=list(dict.fromkeys(json.loads((contracts/'one-full-user.json').read_text())['uncertainty']+one['uncertainty']))
language_user=results/'language-full-user.json'
language_user.write_text(json.dumps({'points':[{'id':i,'meaning':point} for i,point in enumerate(one['meaning'])],'uncertainty':uncertainty}))
remaining=30000-int((time.monotonic()-started)*1000);assert remaining>=1000
language=run('language','LANGUAGE_EXPRESSION',language_user,remaining)
assert set(language['pointIds'])==set(range(len(one['meaning']))) and language['text'].strip()
greeting_ms=int((time.monotonic()-started)*1000);assert greeting_ms<30000
two=run('two','SYSTEM_TWO',contracts/'two-full-user.json',30000)
assert two['points'] and two['sources'] and two['actions']==[] and two['uncertainty']
with open(a.model,'rb') as model_file:model_hash=hashlib.file_digest(model_file,'sha256').hexdigest()
report={'version':1,'backend':'shipping JNI CPU, host only','outputLimit':64,'greetingWallMs':greeting_ms,
        'greetingIncludesTwoSequentialOrgans':True,'systemTwoProof':'separate uncertain-question execution, not a complete deep-reasoning turn',
        'modelSha256':model_hash,
        'fixtureSha256':fixture_hashes,'measurements':measurements,'outputs':outputs,'galaxyAcceptance':'pending'}
(results/'results.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS real greeting and independent reasoning; replay results through Android production parsers')
