# Regenerates validation_parity.tsv from the ORIGINAL Python service.
#   python3 -I tools/generate-validation-fixture.py <path-to-tunarasa>   (run inside ai-client/)
import sys,types,random,csv
for m in ['app','app.services','app.services.metrics_service']: sys.modules[m]=types.ModuleType(m)
class M:
    def __getattr__(s,n): return lambda *a,**k: None
sys.modules['app.services.metrics_service'].metrics_service=M()
import importlib.util
sp=importlib.util.spec_from_file_location('g',sys.argv[1] + '/backend/app/services/gesture_validation_service.py')
g=importlib.util.module_from_spec(sp); sp.loader.exec_module(g)
svc=g.GestureValidationService(); random.seed(7)
texts=[]
for gt in svc.ground_truth_data.values(): texts+= [gt.expected_text]+gt.variations
cases=set(texts)
for t in texts:
    w=t.split(); random.shuffle(w); cases.add(' '.join(w)); cases.add(t.upper()); cases.add(t.replace('a','',1)); cases.add(' '.join(w[:-1]) or 'x')
cases|={'KTP','SIM','tolong','xyz qwerty','  Terima   Kasih ','a'}
rows=[]
for c in sorted(cases):
    for conf in (0.5,0.8,0.95):
        r=svc.validate_gesture_prediction(c,conf)
        m=(r['ground_truth_match'] or {}).get('gesture_id','')
        rows.append([c,conf,int(r['is_correct']),repr(r['similarity_score']),repr(r['accuracy_score']),m])
w=csv.writer(open('src/test/resources/fixtures/validation_parity.tsv','w'),delimiter='\t',lineterminator='\n'); w.writerows(rows); print(len(rows))
