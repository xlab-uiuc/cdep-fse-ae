package configinterface;

import soot.SootMethod;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.SpecialInvokeExpr;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AlluxioInterface implements ConfigInterface {
    public static ConcurrentHashMap<String,String> varToConfig = new ConcurrentHashMap();
    public  boolean isSetter(InvokeExpr iexpr){
        try {
            SootMethod callee = iexpr.getMethod();
            if (callee.getName().startsWith("set")) {
                List<Value> args = iexpr.getArgs();
                if (args.size() == 2 && args.get(0).getType().toString() == "PropertyKey") {
                    return true;
                } else return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public  boolean isGetter(InvokeExpr iexpr) {
        try {
            if (iexpr instanceof SpecialInvokeExpr) {
                SpecialInvokeExpr specialExpr = (SpecialInvokeExpr) iexpr;
                Value base = specialExpr.getBase();
                if (base.toString() == "Builder") {
                    return true;
                }
            }
            if(iexpr.toString().contains("get") && iexpr.toString().contains("alluxio.PropertyKey"))return true;
            return false;
        } catch (Exception e) {
           return false;
        }
    }

    public  String getConfigName(InvokeExpr iexpr){
        List<Value> args = iexpr.getArgs();
        if(varToConfig.containsKey(args.get(0).toString()))return varToConfig.get(args.get(0).toString());
        else return "null";
    }
}
