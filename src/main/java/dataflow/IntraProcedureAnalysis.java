package dataflow;


import configinterface.*;
import utility.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.ExceptionalUnitGraph;
import handlingdep.*;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class IntraProcedureAnalysis  {
    private HashMap<Unit,HashSet<Unit>> dominators;
    public static HashMap<String,String> fields = new HashMap<>(); // keep track of field members
    public static ConcurrentHashMap<String,LinkedList<SootMethod>> filedToMethods =  new ConcurrentHashMap<>();
    IsGetter getter;
    public Recorder recorder;
    HashMap<SootMethod,LinkedList <InvokeExpr>>invokeStmts;
    HashMap<ValueInfo,Value> defaultValues; // for default values
    ConfigInterface interfaces;
    Boolean isEmpty; // the special case for isEmpty() function
    LinkedList<ValueInfo> returns; // the special case for isEmpty() function
    public HashMap<String,Boolean>potential; // for default values
    public HashMap<String,String> fieldToVariable;//mapping from variable name to field name
    private Stmt previousStmt; // for handling switch(stmt){;;;;};
    public HashMap<String,LinkedList<String>>ifScope; // key is if(A), value is if(A){xxxx};
    public HashSet<Value> arrayName ; // recording array name
    LinkedList<IfStmt> ifStmts;
    private HashSet<Pair<String,String>>bufferedUseTogether;


    public static String [] notConsideredCalls = {"google","newInstance","append","format","length","java.","equals","scala.","valueOf"};
    IntraProcedureAnalysis(Recorder recorder_,HashMap<SootMethod,LinkedList <InvokeExpr>> invoke,ConfigInterface interfaces){
        invokeStmts =  invoke;
        recorder = recorder_;
        getter = new IsGetter();
        defaultValues = new HashMap<>();
        isEmpty = new Boolean(false);
        fieldToVariable = new HashMap<>();
        previousStmt = null;
        ifScope = new HashMap<>();
        dominators = new HashMap<>();
        this.interfaces = interfaces;
        this.potential =  new HashMap<>();
        this.ifStmts =  new LinkedList<>();
        bufferedUseTogether =  new HashSet<>();
        arrayName = new HashSet<>();
    }

    public void update(){
        this.potential.clear();
        this.defaultValues.clear();
        this.recorder = new Recorder();
        this.ifStmts.clear();
        this.fieldToVariable.clear();
        this.ifScope.clear();
        this.dominators.clear();
        this.bufferedUseTogether.clear();
        arrayName.clear();
        isEmpty = false;
    }

    private String convert(String a){
        int b = a.indexOf('<');
        return a.substring(b);
    }
    private String transform(String a){
        int b = a.indexOf('<');
        int c = a.indexOf('(');
        return a.substring(b,c)+">";
    }
    private String convertToClassName(String s){
        String result = "";
        for(int i=8;i<s.length()-2;++i){
            if(s.charAt(i)=='/')result+=".";
            else result+=s.charAt(i);
        }
        return result;
    }

    private Boolean reachable(ExceptionalUnitGraph graph, Unit from, Unit to){
        HashSet<Unit> exist = new HashSet<>();
        Queue<Unit> q = new LinkedList<>();
        q.add(from);
        exist.add(from);
        while(!q.isEmpty()){
            Unit front = q.poll();
            if(front.equals(to))return true;
            List<Unit> successors = graph.getUnexceptionalSuccsOf(front);
            for(Unit u:successors){
                if(exist.contains(u))continue;
                else{
                    exist.add(u);
                    q.add(u);
                }
            }
        }
        return false;
    }

    public void analyzeSootMethodIntra(SootMethod sMethod, HashMap<Integer,LinkedList<String>> parameters, HashMap<FunctionWrapper,List<String>> returnValues, ConcurrentHashMap<FunctionWrapper,LinkedList<FunctionWrapper>> callerMethod, Set<FunctionWrapper> callingMethod){
        try {
            Chain<SootClass> interfaces = sMethod.getDeclaringClass().getInterfaces();
            for (SootClass interfaceClass : interfaces) {
                if (DataTransform.superClasses.containsKey(interfaceClass.getName()))
                    DataTransform.superClasses.get(interfaceClass.getName()).add(sMethod.getDeclaringClass().getName());
                else {
                    DataTransform.superClasses.put(interfaceClass.getName(), new HashSet<String>());
                    DataTransform.superClasses.get(interfaceClass.getName()).add(sMethod.getDeclaringClass().getName());
                }
            }
            Body body = sMethod.getActiveBody();
            ExceptionalUnitGraph graph = new ExceptionalUnitGraph(body);
            Iterator<Unit> unitIterator = graph.iterator();
            this.returns = new LinkedList<>();
            while (unitIterator.hasNext()) {
                Unit unit = unitIterator.next();
                if (unit.toString().contains("throw") || unit.toString().contains("warn(java.lang.String)") || unit.toString().contains("error")) {
                    ValueDep cDep = new ValueDep();
                    cDep.dfs(unit, graph, new LinkedList<String>(), recorder, sMethod); // when come across an throw exception/warning -> one more constraint pattern
                }
                handleUnitIntra(sMethod, unit, parameters, returnValues, callerMethod, callingMethod);
                previousStmt = (Stmt) unit;
            }

            /* this is to handle the case when variable associated with field is tainted */
            for (ValueInfo m : recorder.ConfSetByValue.keySet()) {
                if (fieldToVariable.containsKey(m.value.toString())) {
                    String fieldName = fieldToVariable.get(m.value.toString());
                    List<String> stringVals = recorder.getConfsByValueInfo(m);
                    if (!fields.containsKey(fieldName)) fields.put(fieldName, stringVals.get(0));
                }
            }
            /* print true append co-op cases */
            for (Pair<String, String> p : bufferedUseTogether) {
                OutputFormat.output(sMethod.getDeclaringClass(), sMethod, DepNames.useTogether, p.getO1(), p.getO2(), "not just concatenating string");
            }
        }catch (Exception e){
            return;
        }
    }
    private Unit findNearestIf(Unit unit, SootMethod sMethod){
        Queue<Unit>q = new LinkedList<>();
        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(sMethod.getActiveBody());
        List<Unit> preds = graph.getUnexceptionalPredsOf(unit);
        q.addAll(preds);
        while(!q.isEmpty()){
            if(q.element() instanceof IfStmt) {
                List<Unit> targets = graph.getUnexceptionalSuccsOf(q.element());
                int i=0;
                for(;i<targets.size();++i) {
                    if(targets.get(i).toString().contains("exception"))continue;
                    if (reachable(graph, targets.get(i), unit)) continue;
                    else break;
                }
                if(i<targets.size())return q.element();
                else return null;
            }
            List<Unit> tmp = graph.getUnexceptionalPredsOf(q.poll());
            q.addAll(tmp);
        }
        return null;
    }

    private Unit findUniqueIf(Unit unit, SootMethod sMethod){
        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(sMethod.getActiveBody());
        while(graph.getUnexceptionalPredsOf(unit).size()==1){
            Unit preUnit = graph.getUnexceptionalPredsOf(unit).get(0);
            if(preUnit instanceof IfStmt) {
                List<Unit> targets = graph.getUnexceptionalSuccsOf(preUnit);
                int i=0;
                for(;i<targets.size();++i) {
                    if(targets.get(i).toString().contains("exception"))continue;
                    if (reachable(graph, targets.get(i), unit)) continue;
                    else break;
                }
                if(i<targets.size())return preUnit;
                else return null;
            }
            unit = preUnit;
        }
        return null;
    }

    private boolean skip(InvokeExpr expr){
        for(int i=0;i<notConsideredCalls.length;++i){
            if(expr.toString().contains(notConsideredCalls[i]))return true;
        }
        return false;
    }
    private void handleUnitIntra(SootMethod sMethod,Unit unit,HashMap<Integer,LinkedList<String>> parameters,HashMap<FunctionWrapper,List<String>> returnValues,ConcurrentHashMap<FunctionWrapper,LinkedList<FunctionWrapper>> callerMethod,Set<FunctionWrapper>callingMethod){
        Stmt stmt = (Stmt) unit;
        // handling parameters
        if(stmt instanceof DefinitionStmt){
            List<ValueBox> useBoxes = stmt.getUseBoxes();
            List<ValueBox> defBoxes = stmt.getDefBoxes();
            if(useBoxes.size()>0) {
                String paraName = useBoxes.get(0).getValue().toString();
                if (paraName.startsWith("@parameter")) {
                    int i = Integer.parseInt(String.valueOf(paraName.charAt(10)));
                    if(parameters.containsKey(i)) {
                        LinkedList<String> names = parameters.get(i);
                        for(int j=0;j<names.size();++j) {
                            ValueInfo tmpValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, defBoxes.get(0).getValue());
                            recorder.pushValueInfo(names.get(j), tmpValue, stmt);
                        }
                    }
                }
            }
        }

        if(interfaces instanceof AlluxioInterface ){
            if(stmt.toString().contains("PropertyKey") && stmt instanceof  AssignStmt){
                AssignStmt assign = (AssignStmt) stmt;
                String [] splitted = assign.getRightOp().toString().split("\\s+");
                AlluxioInterface.varToConfig.put(assign.getLeftOp().toString(),splitted[2]);
            }
        }

        if(stmt.containsInvokeExpr() && interfaces.isGetter(stmt.getInvokeExpr())){
            if(stmt.getDefBoxes().size()<1)return;
            InvokeExpr expr = stmt.getInvokeExpr();
            List<Value> args = expr.getArgs();
            Value value = null;

            String argValue = interfaces.getConfigName(expr);
            if(argValue == null) return;
            if(DataTransform.classConfig.containsKey(sMethod.getDeclaringClass().getName())){
                DataTransform.classConfig.get(sMethod.getDeclaringClass().getName()).add(argValue);
            }else{
                DataTransform.classConfig.put(sMethod.getDeclaringClass().getName(),new HashSet<String>());
                DataTransform.classConfig.get(sMethod.getDeclaringClass().getName()).add(argValue);
            }

            if(DataTransform.functionConfig.containsKey(sMethod)){
                DataTransform.functionConfig.get(sMethod).add(argValue);
            }else{
                DataTransform.functionConfig.put(sMethod,new HashSet<String>());
                DataTransform.functionConfig.get(sMethod).add(argValue);
            }

            /* pattern one for control dependency, invoke function lives under the scope */
            if(DataTransform.functionScope.containsKey(sMethod))OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.scope,argValue,DataTransform.functionScope.get(sMethod),"this function lives under control");

            /* pattern for control dependency, if(A){B} */
            Unit preUnit = findUniqueIf(unit,sMethod);
            if(preUnit!=null) {
                IfStmt ifStmt = (IfStmt) preUnit;
                if(!ifStmt.toString().contains("null")) {
                    for (int i = 0; i < ifStmt.getUseBoxes().size(); ++i) {
                        ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, ifStmt.getUseBoxes().get(i).getValue());
                        if (recorder.containValueInfo(tmp) && !sMethod.getName().contains("<init>")) {
                            OutputFormat.output(sMethod.getDeclaringClass(), sMethod, DepNames.scope, recorder.getConfsByValueInfo(tmp).get(0), argValue, ifStmt);
                        }
                    }
                }
            }

            ValueInfo tmpValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, stmt.getDefBoxes().get(0).getValue());
            recorder.pushValueInfo(argValue, tmpValue, stmt);


            /* identifying second pattern for default value */
            for(int j=0;j<args.size();++j) {
                tmpValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, expr.getArgBox(j).getValue());
                if (recorder.containValueInfo(tmpValue)) {
                    LinkedList<String> relatedConfigs = recorder.getConfsByValueInfo(tmpValue);
                    for (int k = 0; k < relatedConfigs.size(); ++k) {
                        OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,argValue,relatedConfigs.get(k),stmt);
                    }
                }
                if(args.get(j).getType().toString()=="int"|| args.get(j) instanceof StringConstant){
                    value = args.get(j);
                }
            }
            if(value != null) defaultValues.put(tmpValue,value); // store default value
        }
        else{

            List<ValueBox> useBoxes =  stmt.getUseBoxes();
            List<ValueBox> defBoxes =  stmt.getDefBoxes();
            if(defBoxes.size()==0){
                if( stmt instanceof  ReturnStmt){
                    ValueBox val = useBoxes.get(0);
                    ValueInfo returnValue =  new ValueInfo(sMethod.getDeclaringClass(),sMethod,val.getValue());
                    if(recorder.containValueInfo(returnValue) && !returnValues.containsKey(sMethod)){
                        FunctionWrapper function = new FunctionWrapper(sMethod,parameters);
                        returnValues.put(function,recorder.getConfsByValueInfo(returnValue));
                    }


                    /* this is for handling the third pattern of default value which is noset(A) */
                    if(!returns.contains(returnValue)){
                        returns.add(returnValue);
                    }

                    if(returns.size()==2 && isEmpty ){
                        if(recorder.getConfsByValueInfo(returns.get(0)).size()>0 && recorder.getConfsByValueInfo(returns.get(1)).size()>0)OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,recorder.getConfsByValueInfo(returns.get(0)).get(0),recorder.getConfsByValueInfo(this.returns.get(1)).get(0),stmt);
                        isEmpty = false;
                    }else{
                        if(recorder.getConfsByValueInfo(returns.get(0)).size()==2 && isEmpty){
                            OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,recorder.getConfsByValueInfo(returns.get(0)).get(0),recorder.getConfsByValueInfo(returns.get(0)).get(1),stmt);
                            isEmpty = false;
                        }else{
                            if(isEmpty && HadoopInterface.mappingFromVariableToConfig.containsKey(returns.get(0).value.toString()) && recorder.containValueInfo(returns.get(0))){
                                OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,recorder.getConfsByValueInfo(returns.get(0)).get(0),HadoopInterface.mappingFromVariableToConfig.get(returns.get(0).value.toString()),stmt);
                                isEmpty = false;
                            }
                        }
                    }
                }

                /* for handling false cases from co-op in concatenating strings */
                if(stmt.containsInvokeExpr() && (stmt.toString().contains("info") || stmt.toString().contains("Exception") || stmt.toString().contains("fatal") || stmt.toString().contains("println") || stmt.toString().contains("debug") || stmt.toString().contains("error"))){
                    InvokeExpr exp = stmt.getInvokeExpr();
                    ValueInfo vp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,exp.getArg(0));
                    if(recorder.containValueInfo(vp)){
                        LinkedList<String> strVals = recorder.getConfsByValueInfo(vp);
                        HashSet<Pair<String,String>> tmp = new HashSet<>();
                        for(Pair<String,String> p :bufferedUseTogether){
                            if(strVals.contains(p.getO1()) && strVals.contains(p.getO2()))continue;
                            else{
                                tmp.add(p);
                            }
                        }
                        bufferedUseTogether = tmp;
                    }
                }

                if(stmt.containsInvokeExpr() && stmt.toString().contains("internal.config.ConfigBuilder: void <init>(java.lang.String)>")){
                    InvokeExpr exp = stmt.getInvokeExpr();
                    if(exp instanceof SpecialInvokeExpr){
                        SpecialInvokeExpr virtualExp = (SpecialInvokeExpr) exp;
                        Value base = virtualExp.getBase();
                        if(virtualExp.getArgCount()==1 && virtualExp.getArg(0) instanceof StringConstant){
                            ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,base);
                            recorder.pushValueInfo(((StringConstant) virtualExp.getArg(0)).value,tmp,stmt);
                        }
                    }
                }

                if(stmt.containsInvokeExpr() && !stmt.toString().contains("<java.")){
                    InvokeExpr exp = stmt.getInvokeExpr();
                    if(invokeStmts.containsKey(sMethod));
                    else invokeStmts.put(sMethod,new LinkedList<InvokeExpr>());
                    if(invokeStmts.get(sMethod).contains(exp)){;
                    }else invokeStmts.get(sMethod).add(exp);
                    FunctionWrapper wrapper =  new FunctionWrapper(recorder,exp.getMethod(),sMethod,exp.getArgs());
                    if(!callingMethod.contains(wrapper) && wrapper.args.size()>0){
                        callingMethod.add(wrapper);
                    }
                }

                if(stmt instanceof  IfStmt){
                    IfStmt ifStmt = (IfStmt) stmt;
                    Value condition = ifStmt.getCondition();
                    List<ValueBox> valueBoxes = condition.getUseBoxes();

                    if(valueBoxes.size()==2){
                        ValueInfo left = new ValueInfo(sMethod.getDeclaringClass(),sMethod,valueBoxes.get(0).getValue());

                        if(recorder.containValueInfo(left) && valueBoxes.get(1).getValue().toString().contains("-1")){
                            potential.put(recorder.getConfsByValueInfo(left).get(0),true);
                       }
                    }

                    for(int i=0;i<ifStmts.size();++i){
                        IfStmt ifStmtOne = ifStmts.get(i);
                        if(ifStmtOne.getTarget().toString().equals(ifStmt.getTarget().toString())){
                            ValueBox boxOne = ifStmtOne.getUseBoxes().get(0);
                            ValueBox boxTwo = ifStmt.getUseBoxes().get(0);
                            ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,boxOne.getValue());
                            ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,boxTwo.getValue());
                            if(recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)){
                                OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.useTogether,recorder.getConfsByValueInfo(valueOne).get(0),recorder.getConfsByValueInfo(valueTwo).get(0),stmt);
                            }
                        }
                    }

                    /* for handling return a&&b */
                    Stmt targetStmt = ifStmt.getTarget();
                    if(targetStmt instanceof  AssignStmt && (targetStmt.toString().contains("= 0") ||targetStmt.toString().contains("= 1"))){
                        AssignStmt assignStmt = (AssignStmt) targetStmt;
                        List<ValueBox> conditionBox = ifStmt.getUseBoxes();
                        ValueBox left = assignStmt.getDefBoxes().get(0);
                        ValueInfo leftValue =  new ValueInfo(sMethod.getDeclaringClass(),sMethod,left.getValue());
                        for(int i=0;i<conditionBox.size();++i){
                            ValueInfo valueInfo =  new ValueInfo(sMethod.getDeclaringClass(),sMethod,conditionBox.get(i).getValue());
                            if(recorder.containValueInfo(valueInfo)){
                                List<String> configNames = recorder.getConfsByValueInfo(valueInfo);
                                for(int j=0;j<configNames.size();++j)recorder.pushValueInfo(configNames.get(j),leftValue,stmt);
                            }
                        }
                    }
                    ifStmts.add(ifStmt);


                    /* one pattern for constraint where if(a>b) */
                    List<ValueBox> args = ifStmt.getUseBoxes();
                    if(ValueDep.isCompare(ifStmt.toString()) && !ifStmt.toString().contains("StringBuilder")){
                        ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,args.get(0).getValue());
                        ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,args.get(1).getValue());
                        if(recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)){
                            for(int i=0;i<recorder.getConfsByValueInfo(valueOne).size();++i) {
                                for(int j=0;j<recorder.getConfsByValueInfo(valueTwo).size();++j) {
                                    OutputFormat.output(sMethod.getDeclaringClass(), sMethod, DepNames.constraint, recorder.getConfsByValueInfo(valueOne).get(i), recorder.getConfsByValueInfo(valueTwo).get(j), stmt);
                                }
                            }
                        }
                    }
                }

                /* handle switch stmt */
                if(stmt instanceof SwitchStmt){
                    SwitchStmt switchStmt = (SwitchStmt) stmt;
                    List<Unit>targets = switchStmt.getTargets();
                    LinkedList<String> strVals = new LinkedList<>();
                    ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,switchStmt.getKey());
                    if(recorder.containValueInfo(tmp))strVals = recorder.getConfsByValueInfo(tmp);
                    for(int i=0;i<targets.size();++i){
                        Stmt target = (Stmt) targets.get(i);
                        /* for handling scope dependency */
                        if(target.containsInvokeExpr()){
                            InvokeExpr invoke = target.getInvokeExpr();
                            if(strVals.size()>0){
                                DataTransform.functionScope.put(invoke.getMethod(),new HashSet<>(strVals));//this function lives under this scope
                            }
                        }
                        if(target.toString().contains("new")){
                            if(target.getUseBoxes().size()>0) {
                                ValueBox vB = target.getUseBoxes().get(0);
                                if (vB.getValue() instanceof JNewExpr) {
                                    JNewExpr JNew = (JNewExpr) vB.getValue();
                                    if (strVals.size() > 0) {
                                        DataTransform.classScope.put(JNew.getType().toString(), new HashSet<>(strVals));
                                    }
                                }
                            }
                        }
                        if(target.getDefBoxes().size()>0){
                            ValueInfo lvalue =  new ValueInfo(sMethod.getDeclaringClass(),sMethod,target.getUseBoxes().get(0).getValue());
                            if(strVals.size()>0)recorder.pushValueInfo(strVals.get(0),lvalue,stmt);
                        }
                    }
                }

                /* to handle new Object() case where this is a java class, we just assume this whole object is tainted */
                if(stmt.toString().contains("void <init>") && stmt.toString().contains("<java")){
                    if(stmt.containsInvokeExpr()) {
                        SpecialInvokeExpr specialInvoke = (SpecialInvokeExpr) stmt.getInvokeExpr();
                        ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, specialInvoke.getBase());
                        for (int i = 0; i < specialInvoke.getArgCount(); ++i) {
                            ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, specialInvoke.getArg(i));
                            if (recorder.containValueInfo(tmp)) {
                                List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                                for (int j = 0; j < stringVals.size(); ++j) {
                                    recorder.pushValueInfo(stringVals.get(j), lValue, stmt);
                                }
                            }
                        }
                    }
                }

                /* specially handle add() function */
                if (stmt.toString().contains("add(java.lang.Object)")) {
                    if(stmt.getUseBoxes().size()>0) {
                        ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, stmt.getUseBoxes().get(0).getValue());
                        if (recorder.containValueInfo(tmp)) {
                            ValueBox val = stmt.getUseBoxes().get(1);
                            ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                            List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                            for (int j = 0; j < stringVals.size(); ++j)
                                recorder.pushValueInfo(stringVals.get(j), lValue, stmt);
                        }
                    }
                }

                /* specially to handle java.map.put/get case, only differs by InterfaceInvokeExpr */
                if(stmt instanceof InvokeStmt && stmt.getInvokeExpr() instanceof InterfaceInvokeExpr && stmt.toString().contains("<java.")) {
                    InterfaceInvokeExpr sp = (InterfaceInvokeExpr) stmt.getInvokeExpr();
                    ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, sp.getBase());
                    for(int i=0;i<sp.getArgCount();++i){
                        ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,sp.getArg(i));
                        if(recorder.containValueInfo(tmp)){
                            List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                            for(int j=0;j<stringVals.size();++j)recorder.pushValueInfo(stringVals.get(j),lValue,stmt);
                        }
                    }
                }

                /* specially to handle java.map.put/get case, only differs by VirtualInvokeExpr */
                if(stmt instanceof InvokeStmt && stmt.getInvokeExpr() instanceof VirtualInvokeExpr && stmt.toString().contains("<java.")) {
                    VirtualInvokeExpr sp = (VirtualInvokeExpr) stmt.getInvokeExpr();
                    ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, sp.getBase());
                    for(int i=0;i<sp.getArgCount();++i){
                        ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,sp.getArg(i));
                        if(recorder.containValueInfo(tmp)){
                            List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                            for(int j=0;j<stringVals.size();++j)recorder.pushValueInfo(stringVals.get(j),lValue,stmt);
                        }
                    }
                }

                /* for handling if(configA){set(b,true)} */
                if(stmt.containsInvokeExpr()){
                    InvokeExpr iexpr = stmt.getInvokeExpr();
                    if(interfaces.isSetter(iexpr)){
                        String value = interfaces.getConfigName(iexpr);
                        Unit tmp = findNearestIf(unit,sMethod);
                        if(tmp!=null && !tmp.toString().contains("null")) {
                            List<ValueBox> useboxes = tmp.getUseBoxes();
                            for (int i = 0; i < useboxes.size(); ++i) {
                                ValueInfo val = new ValueInfo(sMethod.getDeclaringClass(), sMethod, useboxes.get(i).getValue());
                                if (recorder.containValueInfo(val)) {
                                    List<String> strVals = recorder.getConfsByValueInfo(val);
                                    for (int j = 0; j < strVals.size(); ++j) {
                                        OutputFormat.output(sMethod.getDeclaringClass(), sMethod, DepNames.constraint, value, strVals.get(j), stmt);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else{
                if(stmt instanceof AssignStmt){
                    AssignStmt assign = (AssignStmt) stmt;
                    Value right = assign.getRightOp();
                    if(right.toString().contains("newarray")){
                        arrayName.add(stmt.getDefBoxes().get(0).getValue());
                    }

                    if (right instanceof InvokeExpr ){

                        if(right.toString().contains("getOrElse") || right.toString().contains("fallbackConf")){

                            List<ValueBox>vals = right.getUseBoxes();
                            ValueInfo tmpOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,vals.get(0).getValue());
                            ValueInfo tmpTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,vals.get(1).getValue());
                            if(recorder.containValueInfo(tmpOne) && recorder.containValueInfo(tmpTwo))OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,recorder.getConfsByValueInfo(tmpOne).get(0),recorder.getConfsByValueInfo(tmpTwo).get(0),stmt);
                        }

                        if(interfaces instanceof SparkInterface){
                            String fieldName = transform(right.toString());
                            if(fields.containsKey(fieldName)){
                                ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,stmt.getDefBoxes().get(0).getValue());
                                recorder.pushValueInfo(fields.get(fieldName),tmp,stmt);
                            }

                        }

                        if(right.toString().contains("internal.config.package") || right.toString().contains("_")){
                            String val = SparkInterface.getConfigNameFromPackage(right.toString());
                            ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,stmt.getDefBoxes().get(0).getValue());
                            recorder.pushValueInfo(val,tmp,stmt);
                            if(!DataTransform.classConfig.containsKey(sMethod.getDeclaringClass().getName())){
                                DataTransform.classConfig.put(sMethod.getDeclaringClass().getName(),new HashSet<String>());
                            }
                            DataTransform.classConfig.get(sMethod.getDeclaringClass().getName()).add(val);
                        }
                        /* handling scope dependency */
                        InvokeExpr expr = (InvokeExpr) right;
                        Unit result = findUniqueIf(unit,sMethod);
                        if(result==null);
                        else{
                            IfStmt ifStmt = (IfStmt) result;
                            List<ValueBox> vals = ifStmt.getUseBoxes();
                            for(int i=0;i<vals.size();++i){
                                ValueInfo val = new ValueInfo(sMethod.getDeclaringClass(),sMethod,vals.get(i).getValue());
                                if(recorder.containValueInfo(val)){
                                    DataTransform.functionScope.put(expr.getMethod(),new HashSet<>(recorder.getConfsByValueInfo(val)));
                                }
                            }
                        }

                        if(right instanceof  InterfaceInvokeExpr){  // for handling interfaceInvokeExpr which is also not covered in analysis
                            ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                            if(recorder.containValueInfo(tmp)){
                                ValueBox val = defBoxes.get(0);
                                ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                                List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                                for (int j = 0; j < stringVals.size(); ++j)
                                    recorder.pushValueInfo(stringVals.get(j), lValue, stmt);
                            }
                        }

                        if (right.toString().contains("add(java.lang.Object)")) {  // specifically handl .add() function
                                ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, right.getUseBoxes().get(0).getValue());
                                if (recorder.containValueInfo(tmp)) {
                                    ValueBox val = right.getUseBoxes().get(1);
                                    ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                                    List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                                    for (int j = 0; j < stringVals.size(); ++j)recorder.pushValueInfo(stringVals.get(j), lValue, stmt);
                                }
                        } else {
                                /* handling getClass function */
                                if(right.toString().contains("getClass(java.lang.String,java.lang.Class,java.lang.Class)") && right.getUseBoxes().get(0).getValue() instanceof StringConstant){
                                    List<ValueBox>vals = right.getUseBoxes();
                                    String configValue = ((StringConstant) vals.get(0).getValue()).value;
                                    for(int j=1;j<=2;++j) {
                                        String strVal = convertToClassName(vals.get(j).getValue().toString());
                                        DataTransform.shouldConsiderSuperClass.add(strVal);
                                        if (!DataTransform.classScope.containsKey(strVal))
                                            DataTransform.classScope.put(strVal, new HashSet<String>());
                                        DataTransform.classScope.get(strVal).add(configValue);
                                    }
                                }
                                /* hanlding constraint pattern one: a.contain(b), membership */
                                if(right.toString().contains("contains(java.lang.Object)") && right.getUseBoxes().size()==2 && !right.toString().contains("StringBuilder")){
                                    ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                                    ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(1).getValue());
                                    if(recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)){
                                        OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,recorder.getConfsByValueInfo(valueOne).getFirst(),recorder.getConfsByValueInfo(valueTwo).get(0),stmt);
                                    }
                                }


                                /* handling constraint pattern two: c = a.get(b); if(c==null){throw exception:}, also membership */
                                if(right.toString().contains("get(java.lang.Object)") && right.getUseBoxes().size()==2){
                                    ValueInfo left = new ValueInfo(sMethod.getDeclaringClass(),sMethod,defBoxes.get(0).getValue());
                                    ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                                    ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(1).getValue());
                                    if (recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)) {
                                        OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,recorder.getConfsByValueInfo(valueOne).getFirst(),recorder.getConfsByValueInfo(valueTwo).getFirst(),stmt);
                                    }
                                }

                                /*  handling constraint pattern three: a=min(b,c) which implies an overwriting relation */
                                if((right.toString().contains("min(") || right.toString().contains("max(")) && right.getUseBoxes().size()==2){
                                    List<ValueBox> values = right.getUseBoxes();
                                    ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,values.get(0).getValue());
                                    ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,values.get(1).getValue());
                                    if(recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)){
                                        OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,recorder.getConfsByValueInfo(valueOne).getFirst(),recorder.getConfsByValueInfo(valueTwo).getFirst(),stmt);
                                    }
                                }

                                if (skip((InvokeExpr) right)) { // do not consider other calls, may need to add more later.
                                    for (int j = 0; j < right.getUseBoxes().size(); ++j) {
                                        ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, right.getUseBoxes().get(j).getValue());
                                        if (recorder.containValueInfo(tmp)) {
                                            ValueBox val = defBoxes.get(0);
                                            ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                                            List<String> stringVals = recorder.getConfsByValueInfo(tmp);
                                            for (int i = 0; i < stringVals.size(); ++i)
                                                recorder.pushValueInfo(stringVals.get(i), lValue, stmt);
                                        }
                                    }

                                    /* deal with co-op, string+string */
                                    if(right.toString().contains("append(java.lang.String)") && !sMethod.toString().contains("confAsString") && !sMethod.toString().contains("toString")){
                                        ValueBox val = defBoxes.get(0);
                                        ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                                        if(recorder.getConfsByValueInfo(lValue).size()>1){
                                            bufferedUseTogether.add(new Pair<>(recorder.getConfsByValueInfo(lValue).get(0),recorder.getConfsByValueInfo(lValue).get(1)));
                                        }
                                    }

                                } else {
                                    if (right.toString().contains("isEmpty")) {
                                        isEmpty =  true;
                                    } else {
                                        if (invokeStmts.containsKey(sMethod)) ;
                                        else invokeStmts.put(sMethod, new LinkedList<InvokeExpr>());
                                        InvokeExpr exp = (InvokeExpr) right;
                                        if (invokeStmts.get(sMethod).contains(exp)) {;
                                        } else invokeStmts.get(sMethod).add(exp);
                                        FunctionWrapper function = new FunctionWrapper(recorder,exp.getMethod(),sMethod,((InvokeExpr) right).getArgs());
                                        if (returnValues.containsKey(function)) {
                                            ValueBox val = defBoxes.get(0);
                                            ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());
                                            List<String> stringVals = returnValues.get(function);
                                            for (int j = 0; j < stringVals.size(); ++j) {
                                                recorder.pushValueInfo(stringVals.get(j), lValue, stmt);
                                            }

                                            if(!DataTransform.functionConfig.containsKey(sMethod))DataTransform.functionConfig.put(sMethod,new HashSet<String>());
                                            if(!DataTransform.classConfig.containsKey(sMethod.getDeclaringClass().getName()))DataTransform.classConfig.put(sMethod.getDeclaringClass().getName(),new HashSet<String>());

                                            for(int j=0;j<stringVals.size();++j){
                                                DataTransform.functionConfig.get(sMethod).add(stringVals.get(j));
                                                DataTransform.classConfig.get(sMethod.getDeclaringClass().getName()).add(stringVals.get(j));
                                            }

                                            if(DataTransform.functionConfig.containsKey(exp.getMethod())){
                                                DataTransform.functionConfig.get(sMethod).addAll(DataTransform.functionConfig.get(exp.getMethod()));
                                            }
                                            if(DataTransform.classConfig.containsKey(exp.getMethod().getDeclaringClass().getName())){
                                                DataTransform.classConfig.get(sMethod.getDeclaringClass().getName()).addAll(DataTransform.classConfig.get(exp.getMethod().getDeclaringClass().getName()));
                                            }
                                        }

                                        if (!callerMethod.containsKey(function)) {
                                            callerMethod.put(function, new LinkedList<FunctionWrapper>());
                                        }
                                        FunctionWrapper currentFunction = new FunctionWrapper(sMethod,parameters);
                                        if (!callerMethod.get(function).contains(currentFunction)) callerMethod.get(function).add(currentFunction);

                                        if(!callingMethod.contains(function) && function.args.size()>0){
                                            callingMethod.add(function);
                                        }
                                    }

                                    if(right.toString().contains("concatPath")){
                                        Unit nearest = findNearestIf((Unit) stmt,sMethod);
                                        if(nearest !=null && nearest instanceof IfStmt){
                                            IfStmt ifStmt = (IfStmt) nearest;
                                            List<ValueBox>args = ifStmt.getUseBoxes();
                                            LinkedList<String>leftStrs = new LinkedList<>();
                                            LinkedList<String>rightStrs = new LinkedList<>();
                                            for(int i=0;i<args.size();++i) {
                                                ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(),sMethod,args.get(i).getValue());
                                                if(recorder.containValueInfo(lValue))leftStrs.addAll(recorder.getConfsByValueInfo(lValue));
                                            }
                                            for(int i=0;i<right.getUseBoxes().size();++i){
                                                ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(i).getValue());
                                                if(recorder.containValueInfo(tmp))rightStrs.addAll(recorder.getConfsByValueInfo(tmp));
                                            }
                                            for(int i=0;i<leftStrs.size();++i){
                                                for(int j=0;j<rightStrs.size();++j){
                                                    OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,leftStrs.get(i),rightStrs.get(j),stmt);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }else{

                        if(stmt.toString().contains("scala.collection.Seq") && right.getUseBoxes().size()>0){
                            ValueInfo rVal = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                            if(recorder.containValueInfo(rVal) && recorder.getConfsByValueInfo(rVal).size()>=2){
                                List<String> strVals = recorder.getConfsByValueInfo(rVal);
                                OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,strVals.get(0),strVals.get(1),stmt);
                            }
                        }

                        /* handle scope dependency, if(A){B} */
                        Unit preUnit = findUniqueIf(unit, sMethod);
                        for(int k=0;k<right.getUseBoxes().size();++k) {
                            ValueInfo rtmp = new ValueInfo(sMethod.getDeclaringClass(), sMethod, right.getUseBoxes().get(k).getValue());
                            if (recorder.containValueInfo(rtmp)) {
                                if (preUnit != null) {
                                    IfStmt ifStmt = (IfStmt) preUnit;
                                    if(!ifStmt.toString().contains("null")) {
                                        List<ValueBox> valBoxs = ifStmt.getUseBoxes();
                                        for (int i = 0; i < valBoxs.size(); ++i) {
                                            ValueInfo tmpInfo = new ValueInfo(sMethod.getDeclaringClass(), sMethod, valBoxs.get(i).getValue());
                                            if (recorder.containValueInfo(tmpInfo)) {
                                                for (int j = 0; j < recorder.getConfsByValueInfo(tmpInfo).size(); ++j)
                                                    if (!sMethod.toString().contains("<init>")) {
                                                        if(recorder.getConfsByValueInfo(tmpInfo).contains(recorder.getConfsByValueInfo(rtmp).get(0)))continue;
                                                        OutputFormat.output(sMethod.getDeclaringClass(), sMethod, DepNames.scope, recorder.getConfsByValueInfo(tmpInfo).get(j), recorder.getConfsByValueInfo(rtmp).get(0), ifStmt);
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if(BehaviorDep.useTogether(right) && right.getUseBoxes().size()>1){
                            ValueInfo first = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                            ValueInfo second = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(1).getValue());
                            if(recorder.containValueInfo(first) && recorder.containValueInfo(second)){
                                OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.useTogether,recorder.getConfsByValueInfo(first).get(0),recorder.getConfsByValueInfo(second).get(0),stmt);
                            }
                        }

                        ValueBox val = defBoxes.get(0);
                        if(val.getValue() instanceof  FieldRef){
                            if(interfaces instanceof  ZooKeeperInterface){
                                for(String a: ZooKeeperInterface.configNames){
                                    if(val.toString().contains(a)){
                                        ValueInfo lValue =  new ValueInfo(sMethod.getDeclaringClass(),sMethod,val.getValue());
                                        if(!recorder.containValueInfo(lValue)){recorder.pushValueInfo(a,lValue,stmt);break;}
                                        else break;
                                    }
                                }
                            }
                        }

                        ValueInfo lValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, val.getValue());

                        /* handling FieldRef */
                        if(right instanceof FieldRef){
                            if(interfaces instanceof  ZooKeeperInterface){
                                for(String a: ZooKeeperInterface.configNames){
                                    if(right.toString().contains(a)) {
                                        if(!recorder.containValueInfo(lValue)){recorder.pushValueInfo(a,lValue,stmt);break;}
                                        else break;
                                    }
                                }
                            }
                            fieldToVariable.put(stmt.getDefBoxes().get(0).getValue().toString(),convert(right.toString()));
                            if(fields.containsKey(convert(right.toString()))){
                                recorder.pushValueInfo(fields.get(convert(right.toString())), lValue, stmt);
                            }
                            if(filedToMethods.containsKey(convert(right.toString()))){
                                if(filedToMethods.get(convert(right.toString())).contains(sMethod));
                                else filedToMethods.get(convert(right.toString())).add(sMethod);
                            }else{
                                filedToMethods.put(convert(right.toString()),new LinkedList<SootMethod>());
                                filedToMethods.get(convert(right.toString())).add(sMethod);
                            }
                        }

                        /* handle constraint, if(xxx) throw exception */
                        if(right.toString().contains("Exception") && ifStmts.size()>0){
                            IfStmt ifStmt = ifStmts.getLast();
                            List<ValueBox> values = ifStmt.getUseBoxes();
                            LinkedList<String> potentials = new LinkedList<>();
                            for(int i=0;i<values.size();++i){
                                ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,values.get(i).getValue());
                                if(recorder.containValueInfo(tmp))potentials.addAll(recorder.getConfsByValueInfo(tmp));
                            }
                            for(int i=0;i<potentials.size();++i){
                                for(int j=i+1;j<potentials.size();++j){
                                    OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,potentials.get(i),potentials.get(j),stmt);
                                }
                            }
                        }

                        /* for handling switch stmt */
                        if(right.getType() instanceof IntegerType){
                            if(previousStmt instanceof  IfStmt){
                                List<ValueBox> args = previousStmt.getUseBoxes();
                                LinkedList<String> strVals = new LinkedList<>();
                                for(int i=0;i<args.size();++i){
                                    ValueInfo tmp = new ValueInfo(sMethod.getDeclaringClass(),sMethod,args.get(i).getValue());
                                    if(recorder.containValueInfo(tmp))strVals.addAll(recorder.getConfsByValueInfo(tmp));
                                }
                                for(int i=0;i<strVals.size();++i)recorder.pushValueInfo(strVals.get(i),lValue,stmt);
                            }
                        }
                        /* handling constaint, pattern A cmp B */
                        if(ValueDep.isCompare(right.toString()) && right.getUseBoxes().size()==2 ){
                            ValueInfo valueOne = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(0).getValue());
                            ValueInfo valueTwo = new ValueInfo(sMethod.getDeclaringClass(),sMethod,right.getUseBoxes().get(1).getValue());
                            if(recorder.containValueInfo(valueOne) && recorder.containValueInfo(valueTwo)){
                                OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.constraint,recorder.getConfsByValueInfo(valueOne).get(0),recorder.getConfsByValueInfo(valueTwo).get(0),stmt);
                            }
                        }

                        for(Value v: arrayName){
                            if(stmt.getDefBoxes().get(0).getValue().toString().contains(v.toString()) && right instanceof  StringConstant){
                                ValueInfo rVal = new ValueInfo(sMethod.getDeclaringClass(),sMethod,v);
                                recorder.pushValueInfo(((StringConstant) right).value,rVal,stmt);
                            }
                        }

                        if(recorder.containValueInfo(lValue) && potential.containsKey(recorder.getConfsByValueInfo(lValue).get(0))){
                                boolean used = false;
                                for(int i=0;i<useBoxes.size();++i){
                                    ValueInfo tmpValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, useBoxes.get(i).getValue());
                                    if(recorder.containValueInfo(tmpValue)){
                                        List<String> strings = recorder.getConfsByValueInfo(tmpValue);
                                        for (int j = 0; j < strings.size(); ++j) {
                                            used = true;
                                            OutputFormat.output(sMethod.getDeclaringClass(),sMethod,DepNames.defaultDep,recorder.getConfsByValueInfo(lValue).get(0),strings.get(j),stmt);
                                        }
                                    }
                                }
                                if(used)potential.remove(recorder.getConfsByValueInfo(lValue).get(0));
                        }
                        else {
                                for (int i = 0; i < useBoxes.size(); ++i) {
                                    ValueInfo tmpValue = new ValueInfo(sMethod.getDeclaringClass(), sMethod, useBoxes.get(i).getValue());
                                    if (recorder.containValueInfo(tmpValue)) {
                                        List<String> strings = recorder.getConfsByValueInfo(tmpValue);
                                        if (strings == null) continue;
                                        if(stmt.getDefBoxes().get(0).getValue() instanceof  FieldRef && strings.size()>0){
                                            String tmp = strings.get(0);
                                            fields.put(convert(stmt.getDefBoxes().get(0).getValue().toString()),tmp);// tainted field recorded
                                        }
                                        for (int j = 0; j < strings.size(); ++j) {
                                            recorder.pushValueInfo(strings.get(j), lValue, stmt);
                                        }
                                    }
                                }
                        }
                        /* handling the case when the name of a configuration parameter is passed by a local variable */
                        if(useBoxes.get(useBoxes.size()-1).getValue() instanceof  StringConstant){
                            StringConstant strVal = ( StringConstant) useBoxes.get(useBoxes.size()-1).getValue();
                            if(ConfigList.allConfigs.contains(strVal.value)){
                                if(useBoxes.get(0).getValue() instanceof JimpleLocal) {
                                    if(!HadoopInterface.mappingFromVariableToConfig.containsKey(useBoxes.get(0).getValue().toString()))
                                    HadoopInterface.mappingFromVariableToConfig.put(useBoxes.get(0).getValue().toString(), strVal.value);
                                    else {
                                        String original = HadoopInterface.mappingFromVariableToConfig.get(useBoxes.get(0).getValue().toString());
                                        if(useBoxes.size()>0 && !original.contains(strVal.value))HadoopInterface.mappingFromVariableToConfig.put(useBoxes.get(0).getValue().toString(), original+";"+strVal.value);
                                    }
                                }
                                else{
                                    if(!HadoopInterface.mappingFromVariableToConfig.containsKey(defBoxes.get(0).getValue().toString()))
                                        HadoopInterface.mappingFromVariableToConfig.put(defBoxes.get(0).getValue().toString(), strVal.value);
                                    else {
                                        String original = HadoopInterface.mappingFromVariableToConfig.get(defBoxes.get(0).getValue().toString());
                                        if(!original.contains(strVal.value))HadoopInterface.mappingFromVariableToConfig.put(defBoxes.get(0).getValue().toString(), original+";"+strVal.value);
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        return;
    }

}