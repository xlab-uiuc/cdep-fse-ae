import csv
import os

dataset = './dataset'
cdep_result = './cDep_result'

def dep(name):
	if "Control" in name or "control" in name:
		return "Control"
	if "Default" in name:
		return "Default Value"
	if "Value" in name:
		return "Value Relationship"
	if "Overwrite" in name:
		return "Overwrite"
	if "Behavior" in name:
		return "Behavioral"
	print name
def readDataSet():
	files = os.listdir(dataset)
	cases = {}
	for file in files:
		if 'hadoop' not in file:
			continue
		f = open(dataset+'/'+file,'r')
		reader = csv.DictReader(f)
		for row in reader:
			dependency = row['Dependency Taxonomy']
			dependency = dep(dependency)
			if dependency not in cases:
				cases[dependency]=[]
			configA = row['Configuration Parameter A'].lower().strip()
			configB = row['Configuration Parameter B( ,C and more)'].lower().strip()
			cases[dependency].append((min(configA,configB),max(configA,configB)))
	return cases
def readToolResult():
	files = os.listdir(cdep_result)
	cases = {}
	for file in files:
		if ".csv" not in file:
			continue
		f = open(cdep_result+'/'+file,'r')
		reader = csv.DictReader(f)
		for row in reader:
			dependency = row['Dependency Taxonomy']
			dependency = dep(dependency)
			if dependency not in cases:
				cases[dependency]=[]
			configA = row['Configuration Parameter A'].lower().strip()
			configB = row['Configuration Parameter B'].lower().strip()
			cases[dependency].append((min(configA,configB),max(configA,configB)))
	return cases

dataset = readDataSet()
tool = readToolResult()

missing = {}
knowTp = {}
newTp = {}
number = {}
toolnumber = {}
for t in dataset:
	missing[t]=0
	number[t] = len(dataset[t])
	toolnumber[t] = len(tool[t])
	for c in dataset[t]:
		if c not in tool[t]:
			missing[t]+=1
for c in toolnumber:
    knowTp[c]=number[c]-missing[c]
for c in toolnumber:
    newTp[c]=toolnumber[c]-knowTp[c]
categories = ["Control","Value Relationship","Overwrite","Default Value","Behavioral"]
for c in categories:
	print c,knowTp[c],number[c],newTp[c]

