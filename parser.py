import sys
import os
import csv
import xml.etree.ElementTree as ET
def buildConfigList():
    configFiles = ["alluxio-default.xml","core-default.xml","hbase-default.xml","hdfs-default.xml","mapred-default.xml","yarn-default.xml","zookeeper-default.xml"]
    configLists = []
    for config in configFiles:
        filePath = "./config_files/"+config
        tree = ET.parse(filePath)
        root = tree.getroot()
        for child in root:
            for name in child:
                if name.tag == "name":
                    if name.text not in configLists:
                        configLists.append(name.text.strip())
    return configLists
def read(fileName,configLists):
    f=open(fileName,'r')
    result = []
    exit = {}
    for line in f:
        if "Soot" in line:
            continue
        content = line.strip().split(';')
        if(len(content)<=3):
            continue
        if(len(content)>6):
            for j in range(6,len(content)):
                content[5]=content[5]+content[j]
        content=content[0:6]
        className = content[0]
        functionName = content[1]
        relation = content[2]
        configA = content[-2]
        configB = content[-3]
        stmt = content[-1]
        if configA == configB:
            continue
        if (configA not in configLists or configB not in configLists):
            continue
        #tuples = (className,functionName,stmt,relation,min(configA,configB),max(configA,configB))
        tuples = (min(configA,configB),max(configA,configB),relation,className,functionName,stmt)
        key = (relation,min(configA,configB),max(configA,configB))
        if key not in exit:
            result.append(tuples)
            exit[key] = 1
    return result
def write(fileName):
    f = open("cDep_result.csv",'w')
    configLists = buildConfigList()
    result = read(fileName,configLists)
    fields = ["parameter A","parameter B","dependency type","java class","java method","jimple stmt"]
    #fields = ["java class","java method","jimple stmt","dependency type","parameter A","parameter B"]
    depNames = ["control","value","overwrite","default","behavior"]
    categorization = {}
    for d in depNames:
        categorization[d]=[]
    for ele in result:
        categorization[ele[2]].append(ele)
    with open("cDep_result.csv",'w') as csvfile:
        csvwriter = csv.writer(csvfile)
        csvwriter.writerow(fields)
        for i in range(len(depNames)):
            cases = categorization[depNames[i]]
            for ele in cases:
                csvwriter.writerow(ele)
write(sys.argv[1])
