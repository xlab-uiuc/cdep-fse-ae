package cdep;

import org.apache.commons.cli.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import handlingdep.*;
import org.xml.sax.SAXException;
import soot.*;
import configinterface.*;
import utility.CheckerConfig;
import dataflow.*;


public class cDep {

    public static Boolean debug = false;
    public static Boolean demo = false;
    public static void main(String[] args) throws IOException, SAXException {

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        Option optionA = Option.builder("o")
                .required(false)
                .desc("This parameter specifies the exported file path. If not specified, output will be directed to stdout.")
                .longOpt("output")
                .hasArg()
                .build();

        Option optionC = Option.builder("a")
                .required(true)
                .desc("Support applications are: hdfs, mapreduce, yarn, hadoop_common, hadoop_tools, hbase, alluxio, zookeeper, spark")
                .longOpt("app")
                .hasArg()
                .build();

        Option optionB = Option.builder("x")
                .required(true)
                .desc("Configuration parameter directory path.")
                .longOpt("xml")
                .hasArg()
                .build();

        Option optionD = Option.builder("d")
                .required(false)
                .desc("whether to enable debugging mode.")
                .longOpt("debug")
                .build();

        Option optionE = Option.builder("demo")
                .required(false)
                .desc("whether to enable demo mode.")
                .longOpt("demo")
                .build();

        options.addOption(optionA);
        options.addOption(optionB);
        options.addOption(optionC);
        options.addOption(optionD);
        options.addOption(optionE);

        try {
            CommandLine commandLine = parser.parse(options, args);

            /* getting optional parameters */
            if(commandLine.hasOption('o')){
                /* getting option o */
                String filePath =  commandLine.getOptionValue('o');
                PrintStream fileOut = new PrintStream(new FileOutputStream(filePath, true));
                System.setOut(fileOut);
            }

            if(commandLine.hasOption('d')){
                debug = true;
            }

            if(commandLine.hasOption("demo")){
                demo = true;
            }

            /* getting required parameters */
            /* getting option x */
            String xmlPath = commandLine.getOptionValue('x');
            File folder = new File(xmlPath);
            File [] xmlFiles = folder.listFiles();
            String [] fileNames = new String[xmlFiles.length];
            for(int i=0;i<xmlFiles.length;++i)fileNames[i]=xmlFiles[i].getAbsolutePath();
            DefaultDep.analyzeConfigFile(fileNames);

            /* getting option a*/
            String [] supported = {"hdfs","mapreduce","yarn","hadoop_common","hadoop_tools","hbase","alluxio","zookeeper","spark"};

            String apps = commandLine.getOptionValue('a');
            String [] result = apps.split(",");
            int [] elements = new int[result.length];
            for(int i=0;i<elements.length;++i) {
                if(result[i]=="")continue;
                int j = 0;
                for(j=0;j<supported.length;++j){
                    if(supported[j].contains(result[i])){
                        break;
                    }
                }
                if(j==supported.length){
                    throw new ParseException(result[i]+" not found in supported application");
                }
                elements[i] = j;
            }

            run(elements);

        }catch (ParseException ex){
            System.out.println(ex.getMessage());
            new HelpFormatter().printHelp("cDep",options);
        }

    }
    public static void run(int [] considered) {
        List<String> srcPaths = new LinkedList<>();
        String classPath = "";
        ConfigInterface interfaces = null;

        if(!demo) {
            for (int i = 0; i < considered.length; ++i) {
                String[] cfg = CheckerConfig.CONFIGS[considered[i]];
                classPath = classPath + CheckerConfig.getClassPath(cfg);
                srcPaths.addAll(CheckerConfig.getSourcePath(cfg));
                interfaces = CheckerConfig.getInterface(cfg);
            }
        }else{
            String[] cfg = CheckerConfig.CONFIGS[2];
            classPath = classPath + CheckerConfig.getClassPath(cfg);
            srcPaths.addAll(CheckerConfig.getSourcePath(cfg));
            interfaces = CheckerConfig.getInterface(cfg);
        }

        String[] initArgs = {
                "-cp", classPath,
                "-pp",
                "-allow-phantom-refs",
                "-app",
                "all-reachable",
                "-f", "n",
        };

        String[] sootArgs = new String[initArgs.length + 2 * srcPaths.size()];
        for (int i = 0; i < initArgs.length; i++) {
            sootArgs[i] = initArgs[i];
        }
        for (int i = 0; i< srcPaths.size(); i++) {
            sootArgs[initArgs.length + 2*i] = "-process-dir";
            sootArgs[initArgs.length + 2*i + 1] = srcPaths.get(i);
        }

        DataTransform transform =  new DataTransform(interfaces);
        PackManager.v().getPack("jtp").add(new Transform("jtp.analysis", transform));
        soot.Main.main(sootArgs);
        transform.analyzeInter();
    }

}
