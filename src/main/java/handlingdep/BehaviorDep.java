package handlingdep;

import soot.Value;

public class BehaviorDep {
    public static String [] binaryOperators = {"+","-","*","/"};
    public static Boolean useTogether(Value right){
        for(int i=0;i<binaryOperators.length;++i){
            if(right.toString().contains(binaryOperators[i]))return true;
        }
        return false;
    }
}
