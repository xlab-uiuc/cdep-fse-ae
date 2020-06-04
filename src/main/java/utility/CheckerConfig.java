package utility;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import configinterface.*;

import assertion.Assert;

public class CheckerConfig {
  
  public final static String HADOOP_PATH = "app/";
  public final static String HBASE_PATH = "app/";
  public final static String Alluxio_PATH = "app/";
  public final static String Spark_PATH = "app/";
  
  public static final String[][] CONFIGS = {
          {
                  "hadoop",
                  "hdfs",
                  HADOOP_PATH + "hadoop-2.9.2/share/hadoop/hdfs/"
          },
          {
                  "hadoop",
                  "mapreduce",
                  HADOOP_PATH + "hadoop-2.9.2/share/hadoop/mapreduce/"
          },
          {
                  "hadoop",
                  "yarn",
                  HADOOP_PATH + "hadoop-2.9.2/share/hadoop/yarn/"
          },
          {
                  "hadoop",
                  "common",
                  HADOOP_PATH + "hadoop-2.9.2/share/hadoop/common/"
          },
    
          {
                  "hadoop",
                  "tools",
                  HADOOP_PATH + "hadoop-2.9.2/share/hadoop/tools/lib/"
          },
          {
                  "hbase",
                  "",
                  HADOOP_PATH + "hbase-2.1.1/lib/"
          },
          {
                  "alluxio",
                  "",
                  HADOOP_PATH + "alluxio-1.8.0/lib/"
          },
          {
                  "zookeeper",
                  "",
                  HADOOP_PATH + "apache-zookeeper-3.5.6-bin/lib/"
          },
          {
                  "spark",
                  "",
                  HADOOP_PATH + "spark-2.4.5-bin-hadoop2.7/jars/"
          },
  };

  public static ConfigInterface getInterface(String []  cfg){
    if(cfg[0].contains("alluxio"))return new AlluxioInterface();
    else{
      if(cfg[0].contains("zookeeper"))return new ZooKeeperInterface();
      if(cfg[0].contains("spark"))return new SparkInterface();
      return new HadoopInterface();
    }
  }
  
  public static String getClassPath(String[] cfg) {
    String clsRoot = "";
    String classPath = "";
    if (cfg[0].compareTo("hadoop") == 0) {
      clsRoot = CheckerConfig.HADOOP_PATH + "/hadoop-2.9.2/";

      if (cfg[1].contains("hdfs")) {
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/hdfs/lib/");
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/hdfs/");
      } else if (cfg[1].contains("yarn")) {
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/yarn/lib/");
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/yarn/");
      } else if (cfg[1].contains("mapreduce")) {
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/mapreduce/lib/");
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/mapreduce/lib-examples/");
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/mapreduce/");
      } else if (cfg[1].contains("tools")) {
        classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/tools/lib/");
      } else if (cfg[1].contains("common")) {
        
      } else {
        Assert.assertImpossible("UNRECOGNIZED COMPONENT: " + cfg[1]);
      }
      
      classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/common/lib/");
      classPath += createSootClassPathPrefix(clsRoot + "/share/hadoop/common/");
    }else{
      if(cfg[0].compareTo("hbase") == 0) {
        clsRoot = CheckerConfig.HBASE_PATH + "/hbase-2.1.1/";
        classPath += createSootClassPathPrefix(clsRoot + "/lib/");
      }else{
        if(cfg[0].compareTo("alluxio") == 0){
          clsRoot = CheckerConfig.Alluxio_PATH + "/alluxio-1.8.0/";
          classPath += createSootClassPathPrefix(clsRoot + "/lib/");
        }
        else{
          if(cfg[0].compareTo("zookeeper") == 0){
            clsRoot = CheckerConfig.Alluxio_PATH + "/apache-zookeeper-3.5.6-bin/";
            classPath += createSootClassPathPrefix(clsRoot + "/lib/");
          }
          if(cfg[0].compareTo("spark") == 0){
            clsRoot = CheckerConfig.Spark_PATH + "/spark-2.4.5-bin-hadoop2.7/";
            classPath += createSootClassPathPrefix(clsRoot + "/jars/");
          }
        }
      }
    }
    return classPath;
  }
  
  public static List<String> getSourcePath(String[] cfg) {
    if (cfg[0].compareTo("hadoop") == 0) {
      return getJars(cfg[2]);
    }
    if(cfg[0].compareTo("hbase")==0){
      return getJarsHbase(cfg[2]);
    }
    if (cfg[0].compareTo("alluxio") == 0) {
      return getJars(cfg[2]);
    }
    if(cfg[0].compareTo("zookeeper")==0){
      return getJars(cfg[2]);
    }
    if(cfg[0].compareTo("spark")==0){
      return getJarsSpark(cfg[2]);
    }
    return null;
  }

  public static List<String> getJarsHbase(String dir) {
    List<String> jars = new LinkedList<String>();
    File sdir = new File(dir);
    Assert.assertTrue(sdir.isDirectory());

    for (File f : sdir.listFiles()) {
      if (f.getName().endsWith(".jar") && !f.getName().contains("test") && f.getName().contains("hbase")){
        jars.add(f.getAbsolutePath());
      }
    }
    return jars;
  }

  public static List<String> getJarsSpark(String dir) {
    List<String> jars = new LinkedList<String>();
    File sdir = new File(dir);
    Assert.assertTrue(sdir.isDirectory());

    for (File f : sdir.listFiles()) {
      if (f.getName().endsWith(".jar") && !f.getName().contains("test") && f.getName().contains("spark")){
        jars.add(f.getAbsolutePath());
      }
    }
    return jars;
  }

  public static List<String> getJars(String dir) {
    List<String> jars = new LinkedList<String>();
    File sdir = new File(dir);
    Assert.assertTrue(sdir.isDirectory());
    
    for (File f : sdir.listFiles()) {
      if (f.getName().endsWith(".jar") && !f.getName().contains("test")){
          jars.add(f.getAbsolutePath());
      }
    }
    return jars;
  }
  
  public static String createSootClassPathPrefix(String dir) {
    String scp = "";
    File fdir = new File(dir);
    if (fdir.isDirectory() == false) {
      System.out.println("[FATAL] " + dir + " should be a directory path");
    }
    File[] files = fdir.listFiles();
    for (int i = 0; i < files.length; i ++) {
      if (files[i].getName().endsWith(".jar")) {
        scp += (":" + files[i].getAbsolutePath());
      }
    }
    return scp;
  }
  
  private static String set2String(Set<String> ss) {
    String res = "";
    for (String e : ss) {
      res += e + ", ";
    }
    return res;
  }
  
  /* For testing purpose */
  public static void main(String[] args) throws IOException {
    System.out.println(new File( "." ).getCanonicalPath());

    HashSet<String> proj = new HashSet<String>();
    HashSet<String> comp = new HashSet<String>();
    HashSet<String> subcomp = new HashSet<String>();
    
    for (String[] cfg : CONFIGS) {
      proj.add(cfg[0]);
      comp.add(cfg[1]);
      subcomp.add(cfg[2]);
    }
    
    System.out.println("Project: " + set2String(proj));
    System.out.println("Components: " + set2String(comp));
    System.out.println("Sub-components: " + set2String(subcomp));
  }
}
