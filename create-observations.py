import json

with open('observation-types.json') as observationTypesFile:
  data = json.load(observationTypesFile)

observationTypes = data["rosetta"]

for observationType in observationTypes:
    
    try:
        refid = observationType["refidOrNewRefid"] 
        code = observationType["cf_CODE10"]
    
        if isinstance(code, int): 
            print "%s(%d)," % (refid, code)
    except:
        # do nothing
        pass


