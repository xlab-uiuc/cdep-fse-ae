package dataflow;

import soot.SootClass;
import soot.SootMethod;
import soot.Value;

public class ValueInfo {
	SootClass usedClass;
	SootMethod usedMethod;
	public Value value;

	ValueInfo(SootClass mClass, SootMethod mMethod, Value mValue) {
		this.usedClass = mClass;
		this.usedMethod = mMethod;
		this.value = mValue;
	}

	public String toString() {
		return usedMethod.toString() + ":\n" + value.toString() + "\n\n";
	}


	public boolean equals(Object aa){
        if(aa instanceof ValueInfo){
        	ValueInfo b = (ValueInfo)aa;
        	if(this.usedClass ==b.usedClass)
        		if(this.value.equals(b.value)) {
        			if(isStaticValue(this.value.toString()))
        				return true;
        			else if(this.usedMethod == b.usedMethod)
        				return true;
        		}

        }
        return false;
    }
	public int hashCode() {
		if(isStaticValue(this.value.toString()))
			return this.usedClass.hashCode()*this.value.equivHashCode();
		else
			return this.usedClass.hashCode()*this.value.equivHashCode()*this.usedMethod.equivHashCode();
	}
	public boolean isStaticValue(String name) {
		if(name.contains(usedClass.getName()) && name.indexOf(".<")>=0)
			return true;
		else
			return false;
	}
};
