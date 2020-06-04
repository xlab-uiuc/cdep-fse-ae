#!/bin/bash
while getopts ":a:" opt; do
	case ${opt} in
		a) 
			target=$OPTARG
			;;
		*)
			echo "Usage: run.sh -a x (x is any or a combination of the following options separated by ',')"
                        echo "       hdfs"
                        echo "       mapreduce"
                        echo "       yarn" 
                        echo "       hadoop_common"
                        echo "       hadoop_tools" 
                        echo "       hbase" 
                        echo "       alluxio" 
                        echo "       zookeeper" 
                        echo "       spark"
			exit 1
			;;
	esac
done

if [ -z "${target}" ]; then
	echo "Usage: run.sh -a x (x is any or a combination of the following options separated by ',')"
        echo "       hdfs"
        echo "       mapreduce"
        echo "       yarn" 
        echo "       hadoop_common"
        echo "       hadoop_tools" 
        echo "       hbase" 
        echo "       alluxio" 
        echo "       zookeeper" 
        echo "       spark"
	exit 1
fi

echo "The program is running and output is cDep_result.csv"
mvn exec:java -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dexec.mainClass="cdep.cDep" -Dexec.args="-o tmp.txt -x ./config_files -a ${target}"
python parser.py tmp.txt
if [ ! -d "/tmp/output" ]; then
    mkdir /tmp/output
fi
mv cDep_result.csv /tmp/output/
