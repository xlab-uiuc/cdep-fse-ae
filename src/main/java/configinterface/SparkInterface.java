package configinterface;

import soot.SootMethod;
import soot.Value;
import soot.jimple.*;

import java.util.List;

public class SparkInterface implements ConfigInterface {
    public static String getConfigNameFromPackage(String a){
        String [] result = a.split(" ");
        String b = result[result.length-1];
        if(b.indexOf("(")==-1)return "";
        return b.substring(0,b.indexOf("("));
    }

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
        }
        return null;
    }

    public boolean isGetter(InvokeExpr iexpr) {
            SootMethod callee = iexpr.getMethod();
            if (callee.getName().startsWith("get") && callee.getDeclaringClass().getName().contains("SparkConf")) {
                for(int i=0;i<iexpr.getArgCount();++i)if(iexpr.getArg(i) instanceof  StringConstant) return true;
            }
            return false;
    }
}
