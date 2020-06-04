package dataflow;

import cdep.cDep;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;


public class Recorder{
	public HashMap <String,LinkedList<ValueInfo>> ConfSet; // conf and its tainted variables
	public HashMap <ValueInfo, LinkedList<String>> ConfSetByValue; // SootValue and its conf
	public HashMap <ValueInfo, LinkedList<Stmt>> ConfStmtByValue; // SootValue and its associated stmt
	public HashMap <SootMethod, String> MethodResConf; // SootMethod and its return value
	public HashMap <SootMethod, ValueInfo> MethodRes; // SootMethod and its return SootValue
	public HashMap <Pair<SootMethod,List<ValueInfo>>,ValueInfo> taintMethodReturnValue; // taint the function return value, key is function name and its parameter
	public LinkedList<String> consideredParameters; // this is for storing the conf parameters we care about. This is only for improving efficiency
	public String save;
	public String DefaultDeps;
	

	public Recorder(){
		recorderInit();
	}
	public void recorderInit(){
		ConfSet = new HashMap<String,LinkedList<ValueInfo>>();
		ConfSetByValue = new HashMap<ValueInfo, LinkedList<String>>();
		ConfStmtByValue = new HashMap<>();
		MethodResConf = new HashMap<SootMethod, String>();
		MethodRes = new HashMap<SootMethod, ValueInfo>();
		taintMethodReturnValue =  new HashMap<>();
		consideredParameters = new LinkedList<>();

		save = new String();
		DefaultDeps= new String();

		if(cDep.debug){ }
	}

	public boolean containsKey(ValueInfo tmpValue) {
		for(ValueInfo mv : ConfSetByValue.keySet()) {
			if(mv.equals(tmpValue))
				return true;
		}
		Value v;
		return false;
	}

	public Boolean containValueInfo(ValueInfo mValueInfo) {
		for(ValueInfo v : ConfSetByValue.keySet()){
			if(v.usedClass.getName()==mValueInfo.usedClass.getName() && v.usedMethod.getName()==mValueInfo.usedMethod.getName() && v.value.equivTo(mValueInfo.value)){
				return true;
			}
		}
		return false;
	}

	public LinkedList<String> getConfsByValueInfo(ValueInfo mValueInfo){
		LinkedList<String> res = new LinkedList<String>();
		for(ValueInfo mv : ConfSetByValue.keySet()) {
			if(valueEquals(mv.value, mValueInfo.value))
				return ConfSetByValue.get(mv);
		}
		return res;
	}
	
	public LinkedList<String> containsValue(Value mValue) {
		LinkedList<String> res = new LinkedList<String>();
		for(ValueInfo mv : ConfSetByValue.keySet()) {
			if(valueEquals(mv.value, mValue))
				return ConfSetByValue.get(mv);
		}
		return res;
	}

	public boolean containValue(Value value,SootMethod method,SootClass _class){
		ValueInfo tmp = new ValueInfo(_class,method,value);
		return this.containValueInfo(tmp);
	}
	public LinkedList<String>getByValue(Value value,SootMethod method,SootClass _class){
		ValueInfo tmp = new ValueInfo(_class,method,value);
		return this.getConfsByValueInfo(tmp);
	}
	
	public void pushValueInfo(String key, ValueInfo tmpValue, Stmt stmt) {
		if(cDep.debug){
			if(!consideredParameters.contains(key)) return; // not consider this parameter now.
		}
		addConfValue(key, tmpValue);
		addConfString(key, tmpValue);
	}

	private void addConfValue(String key,ValueInfo tmpValue) {
		if(this.ConfSet.containsKey(key)) {
			if(!this.ConfSet.get(key).contains(tmpValue))
				this.ConfSet.get(key).add(tmpValue);
		}else {
			LinkedList<ValueInfo> m =new LinkedList<>();
			m.add(tmpValue);
			this.ConfSet.put(key, m);
		}
	}

	private void addConfString(String key,ValueInfo tmpValue) {
		if(this.ConfSetByValue.containsKey(tmpValue)) {
			if(!this.ConfSetByValue.get(tmpValue).contains(key))
				this.ConfSetByValue.get(tmpValue).add(key);
		}else {
			LinkedList<String> m =new LinkedList<>();
			m.add(key);
			this.ConfSetByValue.put(tmpValue, m);
		}
	}

	// NOTE: we should judge whether two value are equaled by value.equivTo().
	public static boolean valueEquals(Value a, Value b) {
		if(a.equivHashCode()==b.equivHashCode())
			return true;

		if(a.equivTo(b))
			return true;
		else
			return false;
	}

	public Stmt intersect(LinkedList<Stmt>stmtOne,LinkedList<Stmt>stmtTwo){
		for(Stmt a: stmtOne){
			if(stmtTwo.contains(a))return a;
		}
		return null;
	}

	public Stmt related(String keyOne, String keyTwo){
		LinkedList<ValueInfo> valueOne = this.ConfSet.get(keyOne);
		LinkedList<ValueInfo> valueTwo = this.ConfSet.get(keyTwo);
		for(ValueInfo valOne :valueOne){
			LinkedList<Stmt> stmtOne = ConfStmtByValue.get(valOne);
			for(ValueInfo valTwo: valueTwo){
				if(valOne.equals(valTwo))continue;
				else{
					LinkedList<Stmt> stmtTwo = ConfStmtByValue.get(valTwo);
					Stmt result = intersect(stmtOne,stmtTwo);
					if(result!=null)return result;
				}
			}
		}
		return null;
	}
}

