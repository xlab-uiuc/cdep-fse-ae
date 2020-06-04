package utility;

import configinterface.ConfigList;
import handlingdep.DepNames;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Expr;
import soot.jimple.Stmt;
import java.util.HashSet;


public class OutputFormat {

    public static void output(SootClass className, SootMethod methodName, String relation,String configOne, String configTwo, Stmt stmt){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo) && configOne!=configTwo) {
            System.out.println(className.toString() + ";" + methodName + ";" + relation + ";" + configOne + ";" + configTwo + ";" + stmt);
        }
    }

    public static void output(SootClass className, SootMethod methodName, String relation,String configOne, String configTwo, Expr expr){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo)  && configOne!=configTwo ) {
            System.out.println(className.toString() + ";" + methodName + ";" + relation + ";" + configOne + ";" + configTwo + ";" + expr);
        }
    }

    public static void output(SootClass className, SootMethod methodName, String relation,String configOne, String configTwo, String expr){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo)  && configOne!=configTwo) {
            System.out.println(className.toString() + ";" + methodName + ";" + relation + ";" + configOne + ";" + configTwo + ";" + expr);
        }
    }

    public static void output(SootClass className, SootMethod methodName, String relation,String configOne, String configTwo){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo)  && configOne!=configTwo ) {
            System.out.println(className.toString() + ";" + methodName + ";" + relation + ";" + configOne + ";" + configTwo + ";*");
        }
    }
    public static void output(String className,String relation,String configOne, String configTwo){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo)  && configOne!=configTwo ) {
            System.out.println(className + ";" + "*;" + relation + ";" + configOne + ";" + configTwo + ";*");
        }
    }
    public static void output(String className,String relation,String configOne,  HashSet<String> configTwo){
        for(String s: configTwo) output(className,relation,configOne,s);
    }

    public static void output(SootClass className, SootMethod methodName, String relation, String configOne, HashSet<String> configTwo, Stmt stmt){
        for(String s: configTwo) output(className,methodName,relation,configOne,s,stmt);
    }

    public static void output(SootClass className, SootMethod methodName, String relation, String configOne, HashSet<String> configTwo, String stmt){
        for(String s: configTwo) output(className,methodName,relation,configOne,s,stmt);
    }
    public static void output(String configOne, String configTwo){
        if(ConfigList.isConfig(configOne) && ConfigList.isConfig(configTwo) && configOne!=configTwo) {
            System.out.println("*;*;" + DepNames.defaultDep + ";" + configOne + ";" + configTwo + ";*");
        }
    }
}
