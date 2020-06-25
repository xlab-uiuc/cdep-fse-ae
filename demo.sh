mvn exec:java -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dexec.mainClass="cdep.cDep" -Dexec.args="-o tmp.txt -x ./config_files -a yarn -demo"
python parser.py tmp.txt
if [ ! -d "/tmp/output" ]; then
    mkdir /tmp/output
fi
mv cDep_result.csv /tmp/output/
