package dataflow;

import cdep.cDep;
import configinterface.*;
import soot.*;
import handlingdep.*;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import utility.OutputFormat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static configinterface.HadoopInterface.mappingFromVariableToConfig;


public class DataTransform extends BodyTransformer {
    public LinkedList<String> consideredFunction;
    public LinkedList<String> consideredClass;
    public ConfigInterface interfaces;
    public static HashMap<FunctionWrapper,List<String>> returnValues;
    public ConcurrentHashMap<FunctionWrapper,LinkedList<FunctionWrapper>> calledMethod; // record called method
    public HashSet<FunctionWrapper> callingMethod; // record caller method;
    public ConcurrentHashMap<SootMethod,Body> methodBody;
    public static ConcurrentHashMap<SootMethod,HashSet<String>> functionScope = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String,HashSet<String>> classScope = new ConcurrentHashMap<>(); //key is classname, value is control config
    public static ConcurrentHashMap<String,HashSet<String>>classConfig = new ConcurrentHashMap<>();// key is classname, value is list of string under that class
    public static ConcurrentHashMap<SootMethod,HashSet<String>>functionConfig = new ConcurrentHashMap<>();// key is method, value is list of string under that class
    public static ConcurrentHashMap<String,HashSet<String>>superClasses = new ConcurrentHashMap<>();// each class name and its associated super class
    public static HashSet<String>shouldConsiderSuperClass = new HashSet<>();//should consider its super class

    public DataTransform(ConfigInterface interfaces){
        this.interfaces = interfaces;
        this.returnValues = new HashMap<>();
        this.calledMethod = new ConcurrentHashMap<>();
        this.callingMethod = new HashSet<>();
        this.methodBody =  new ConcurrentHashMap<>();
        this.consideredClass = new LinkedList<>();
        this.consideredFunction = new LinkedList<>();
        if(cDep.debug){ ;}
    }
    public void analyzeInter(){
        HashMap<FunctionWrapper,List<String>> beforeReturnValue = new HashMap<>();
        HashSet<FunctionWrapper>beforeCalling = new HashSet<>();
        HashMap<String,String> previousField = new HashMap<>();

        Recorder recorder = new Recorder();
        HashMap<SootMethod,LinkedList <InvokeExpr>> invoke = new LinkedHashMap<>();
        IntraProcedureAnalysis intra = new IntraProcedureAnalysis(recorder,invoke,interfaces);
        Boolean change = true;

        while(change) {
            change = false;
            Boolean subChange = true;

            while (subChange) {
                subChange = false;
                HashMap<FunctionWrapper, List<String>> temp = (HashMap<FunctionWrapper, List<String>>) returnValues.clone();
                for (FunctionWrapper s : temp.keySet()) {
                    if (!beforeReturnValue.containsKey(s)) {
                        if (calledMethod.containsKey(s)) {
                            subChange = true;
                            change = true;
                            LinkedList<FunctionWrapper> methods = calledMethod.get(s);
                            for (int i = 0; i < methods.size(); ++i) {
                                try {
                                    methods.get(i).getMethod().setActiveBody(this.methodBody.get(methods.get(i).getMethod())); // set the current active body
                                }catch (Exception e){
                                    continue;
                                }
                                intra.update();
                                intra.analyzeSootMethodIntra(methods.get(i).getMethod(), methods.get(i).getArgs(), returnValues, calledMethod, callingMethod);
                                mappingFromVariableToConfig.clear();
                            }
                        }
                    }
                }
                beforeReturnValue = temp;
            }

            Boolean subChangeTwo = true;
            while(subChangeTwo){
                subChangeTwo = false;
                HashSet<FunctionWrapper> temp = (HashSet<FunctionWrapper>) callingMethod.clone();
                for(FunctionWrapper s : temp){
                    if(!beforeCalling.contains(s)){
                        try {
                            subChangeTwo = true;
                            change = true;
                            s.method.setActiveBody(this.methodBody.get(s.getMethod()));
                            intra.update();
                            intra.analyzeSootMethodIntra(s.method, s.getArgs(), returnValues, calledMethod, callingMethod);
                            mappingFromVariableToConfig.clear();
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
                beforeCalling = temp;
            }

            if(!previousField.equals(IntraProcedureAnalysis.fields)) {
                for (String s : IntraProcedureAnalysis.filedToMethods.keySet()) {
                    if (IntraProcedureAnalysis.fields.containsKey(s)) {
                        for (int i = 0; i < IntraProcedureAnalysis.filedToMethods.get(s).size(); ++i) {
                            try {
                                IntraProcedureAnalysis.filedToMethods.get(s).get(i).setActiveBody(this.methodBody.get(IntraProcedureAnalysis.filedToMethods.get(s).get(i)));
                                intra.update();
                                intra.analyzeSootMethodIntra(IntraProcedureAnalysis.filedToMethods.get(s).get(i), new HashMap<Integer, LinkedList<String>>(), returnValues, calledMethod, callingMethod);
                            }catch (Exception e){
                                continue;
                            }
                        }
                    }
                }
                change = true;
            }
            previousField = (HashMap<String,String>) IntraProcedureAnalysis.fields.clone();
        }

        for(String s:superClasses.keySet()){
            if(classScope.containsKey(s) && shouldConsiderSuperClass.contains(s)){
                HashSet<String>classNames = superClasses.get(s);
                for(String tmp:classNames){
                    if(classScope.containsKey(tmp))classScope.get(tmp).addAll(classScope.get(s));
                    else classScope.put(tmp,new HashSet<>(classScope.get(s)));
                }
            }
        }

    }
    protected void internalTransform(Body body, String phase, Map<String, String> options){
        Recorder recorder = new Recorder();
        HashMap<SootMethod,LinkedList <InvokeExpr>> invoke = new LinkedHashMap<>();
        SootMethod sourceMethod = body.getMethod();
        this.methodBody.put(sourceMethod,body);
        if(cDep.debug){
            boolean tmp = false;
            for(String c:consideredClass){
                if(sourceMethod.getDeclaringClass().getName().contains(c)){
                    tmp = true;
                    break;
                }
            }
            for(String f: consideredFunction) {
                if (sourceMethod.getName().contains(f)) {
                    tmp = true;
                    break;
                }
            }
            if(!tmp)return;
        }
        IntraProcedureAnalysis intra = new IntraProcedureAnalysis(recorder, invoke,interfaces);
        intra.analyzeSootMethodIntra(sourceMethod, new HashMap<Integer, LinkedList<String>>(),returnValues,calledMethod,callingMethod);
        mappingFromVariableToConfig.clear();
        /* checking one kind of overwriting relationship with set() interface */
        for(SootMethod method: invoke.keySet()){
            for(InvokeExpr exp : invoke.get(method)){
                if(interfaces.isSetter(exp)){
                    LinkedList<ValueInfo> argsInfo = new LinkedList<>();
                    int count =  exp.getArgCount();
                    for(int j=0;j<count;++j){
                        argsInfo.add(new ValueInfo(method.getDeclaringClass(),method,exp.getArgBox(j).getValue()));
                    }
                    if(recorder.containValueInfo(argsInfo.get(1))) {
                        StringConstant str = (StringConstant) argsInfo.get(0).value;
                        if(intra.potential.containsKey(str.value)) OutputFormat.output(sourceMethod.getDeclaringClass(),sourceMethod,DepNames.defaultDep,str.value,recorder.getConfsByValueInfo(argsInfo.get(1)).get(0),exp);
                        else{
                            OutputFormat.output(sourceMethod.getDeclaringClass(),sourceMethod,DepNames.overwriteDep,str.value,recorder.getConfsByValueInfo(argsInfo.get(1)).get(0),exp);
                        }
                    }
                }
            }
        }
    }
}
