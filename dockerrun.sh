#!/bin/bash
while getopts ":a:" opt; do
    case ${opt} in
        a)
            target=$OPTARG
            ;;
        *)
            echo "Usage: dockerrun.sh -a x (x is any or a combination of the following options separated by ',')"
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
    echo "Usage: dockerrun.sh -a x (x is any or a combination of the following options separated by ',')"
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

docker run -m 12G --oom-kill-disable -e TARGET=$target -v /tmp/output:/tmp/output cdep/cdep:1.0
