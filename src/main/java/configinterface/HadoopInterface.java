package configinterface;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;

public class HadoopInterface implements ConfigInterface {
  public static final String superConfigClass = "org.apache.hadoop.conf.Configuration";
  public static String [] blackList = {"getNameServiceUris","getClassByName","getClass"};
	public static String [] whitelist = {"getPassword","longOption"};
  public static ConcurrentHashMap<String,String> mappingFromVariableToConfig =  new ConcurrentHashMap<>();
  
  /**
   * Return if the given class is a subclass that inherits the superConfigClass
   * @param cls
   * @return
   */
  public static boolean isSubClass(SootClass cls) {
    if (cls.toString().contains(superConfigClass)) {
      return true;
    }
    if (cls.hasSuperclass() && cls.getSuperclass().toString().contains(superConfigClass)) {
      return true;
    }
    return false;
  }
  
  /**
   * Return if the statement is a getter statement
   * @param iexpr
   * @return
   */
  public boolean isSetter(InvokeExpr iexpr){
  	try {
		SootMethod callee = iexpr.getMethod();
		if (callee.getName().startsWith("set")) {
			List<Value> args = iexpr.getArgs();
			if (args.size() == 2 && args.get(0) instanceof StringConstant) {
				return true;
			} else return false;
		}
		return false;
	} catch (Exception e) {
		return false;
	}
  }
  public String getConfigName(InvokeExpr iexpr){
  	for(int i=0;i<iexpr.getArgCount();++i) {
  		if(iexpr.getArg(i) instanceof  StringConstant) {
			Value name = iexpr.getArg(i);
			StringConstant strVal = (StringConstant) name;
			return strVal.value;
		}
  		if(mappingFromVariableToConfig.containsKey(iexpr.getArg(i).toString())){
  			return mappingFromVariableToConfig.get(iexpr.getArg(i).toString());
		}
	}
  	return null;
  }

  public boolean isGetter(InvokeExpr iexpr) {
  	try {
		SootMethod callee = iexpr.getMethod();
		for(int i=0;i<whitelist.length;++i)if(callee.getName().contains(whitelist[i]))return true;
		if (callee.getName().startsWith("get")) {
			for(int i=0;i<blackList.length;++i)if(callee.getName().contains(blackList[i]))return false;

			List<Value> args = iexpr.getArgs();
			boolean hasConf = false;
			boolean hasStr = false;
			if(args.size()==0)return false;
			if(args.size()>3)return false;
			for (int i = 0; i < args.size(); ++i) {
				Value arg = args.get(i);
				if (arg instanceof StringConstant || mappingFromVariableToConfig.containsKey(arg.toString()))
					hasStr = true;
				if (arg.getType() instanceof RefType) {
					RefType rty = (RefType) arg.getType();

					if (isSubClass(rty.getSootClass())) {
						hasConf = true;
					}
				}
			}
			if (hasConf && hasStr)
				return true;

			if (iexpr instanceof InstanceInvokeExpr) {
				Value base = ((InstanceInvokeExpr) iexpr).getBase();
				if (base.getType() instanceof RefType) {
					RefType rty = (RefType) base.getType();
					if (isSubClass(rty.getSootClass())) {
						return true;
					}
				}
			} else if (iexpr instanceof StaticInvokeExpr) {
				;
			} else if (iexpr instanceof VirtualInvokeExpr) {
				// pass
			}
		}
		return false;
	} catch (Exception e) {
		return false;
	}

  }
}
