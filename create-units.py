import json

with open('unit-codes.json') as unitCodesFile:
  data = json.load(unitCodesFile)

model = data["model"]
units = model["units"]

for unit in units:
    code = unit["CF_UCODE10"]
    
    if isinstance(code, int): 
        newrefid = unit["newrefid"]
        refid = unit["refid"]
        outputID = refid 
        if len(refid) == 0:
             outputID = newrefid
      
        print "%s(%d, \"%s\", \"%s\")," % (outputID, unit["CF_UCODE10"], unit["symbol"], unit["description"])
    


