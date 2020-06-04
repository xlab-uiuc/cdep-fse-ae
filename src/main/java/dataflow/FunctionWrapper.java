package dataflow;

import soot.SootMethod;
import soot.Value;
import soot.jimple.StringConstant;
import configinterface.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class FunctionWrapper {
    SootMethod method;
    HashMap<Integer, LinkedList<String>>args;
    public FunctionWrapper(Recorder recorder, SootMethod method, SootMethod callingMethod,List<Value> args){
        this.args =  new HashMap<>();
        this.method =  method;
        for(int i=0;i<args.size();++i){
            ValueInfo tmp = new ValueInfo(callingMethod.getDeclaringClass(),callingMethod,args.get(i));
            if(recorder.containValueInfo(tmp))this.args.put(i,recorder.getConfsByValueInfo(tmp));
            if(args.get(i) instanceof StringConstant ){
                StringConstant sVal = (StringConstant) args.get(i);
                if(ConfigList.allConfigs.contains(sVal.value)) {
                    LinkedList<String> value = new LinkedList<>();
                    value.add(sVal.value);
                    this.args.put(i, value);
                }
            }
        }
    }
    public FunctionWrapper(SootMethod method,HashMap<Integer,LinkedList<String>> args){
        this.method =  method;
        this.args =  args;
    }
    public SootMethod getMethod(){
        return method;
    }
    public HashMap<Integer,LinkedList<String>> getArgs(){
        return args;
    }
    private boolean equals(HashMap<Integer, LinkedList<String>>argOne, HashMap<Integer, LinkedList<String>>argTwo){
        if(argOne.size()!=argTwo.size())return false;
        for(Integer key: argOne.keySet()){
            if(!argTwo.containsKey(key))return false;
            else {
                if (argTwo.get(key).size()!=argOne.get(key).size()) return false;
                {
                    for(String s: argTwo.get(key)){
                        if(argOne.get(key).contains(s))continue;
                        else return false;
                    }
                    return true;
                }
            }
        }
        return true;
    }
    @Override
    public boolean equals(Object o){
        if(o instanceof FunctionWrapper){
            FunctionWrapper oFunction = (FunctionWrapper) o;
            if(oFunction.getMethod().getName().equals(this.method.getName()) && equals(args,oFunction.args)){ return true;
            }else return false;
        }else return false;
    }
    @Override
    public int hashCode() {
        return this.method.hashCode()*37+this.args.hashCode()*7;
    }
}
