#!/usr/bin/env python3
"""Extract the shipping Kotlin contracts/prompts for host real-engine proof.
Run from repository root: python3 scripts/engine-proof/prepare_contracts.py /tmp/yuki-contracts
"""
import json,re,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
grammar=(root/'app/src/main/kotlin/com/mavyy/localyuki/inference/OrganGrammar.kt').read_text()
common=re.search(r'private val common="""(.*?)"""',grammar,re.S).group(1).strip()
common='\n'.join('source ::= "\\\"input\\\""' if line.startswith('source ::=') else line for line in common.splitlines())
cognition=(root/'app/src/main/kotlin/com/mavyy/localyuki/cognition/NeuralCognition.kt').read_text()
for role,enum,klass in [('one','SYSTEM_ONE','NeuralSystemOne'),('two','SYSTEM_TWO','NeuralSystemTwo'),('language','LANGUAGE_EXPRESSION','NeuralExpression')]:
 rules=re.search(r'ModelRole\.'+enum+r' -> """(.*?)"""',grammar,re.S).group(1).strip()
 if role=='two':rules='\n'.join('actions ::= "[" ws "]"' if line.startswith('actions ::=') else line for line in rules.splitlines())
 (out/(role+'.gbnf')).write_text(rules+'\n'+common+'\n')
 fragment=cognition[cognition.index('internal class '+klass):]
 prompt=re.search(r'\n            "((?:[^"\\]|\\.)*)",',fragment).group(1)
 (out/(role+'-system.txt')).write_text(json.loads('"'+prompt+'"'))
base={'identity':{'self':'Yuki','relationship':'Mavyy, primary partner','personality':['warmth, independent judgment, honesty, curiosity']},'ownerInput':'Hello, Yuki.','time':'2026-10-06T18:00:00Z','memories':[],'observations':[],'recentConversation':[],'ongoingContext':[],'affect':'neutral','uncertainty':[],'availableCapabilities':[]}
(out/'one-user.json').write_text(json.dumps(base))
base['ownerInput']='I have a headache. Is its cause certain?'
(out/'two-user.json').write_text(json.dumps(base))
(out/'language-user.json').write_text(json.dumps({'points':[{'id':0,'meaning':'Hello, Mavyy. I am glad you are here.'}],'uncertainty':[]}))
