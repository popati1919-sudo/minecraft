package ca.larperscoin.smp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/** Pure rules shared by the ledger and its regression tests. */
final class AntiDuplication {
    static final long MAX_UNITS=100_000_000_000_000L;
    private AntiDuplication(){}
    static long units(double amount){
        if(!Double.isFinite(amount))throw new IllegalArgumentException("Non-finite amount");
        long result=BigDecimal.valueOf(amount).movePointRight(4).setScale(0,RoundingMode.HALF_UP).longValueExact();
        if(result < -MAX_UNITS || result > MAX_UNITS)throw new IllegalArgumentException("Amount exceeds ledger limit");
        return result;
    }
    static double amount(long units){return BigDecimal.valueOf(units,4).doubleValue();}
    static double add(double before,double delta){
        long next=Math.addExact(units(before),units(delta));
        if(next<0||next>MAX_UNITS)throw new IllegalArgumentException("Invalid balance");
        return amount(next);
    }
    enum Recovery { APPLY, ALREADY_APPLIED, CONFLICT }
    static Recovery recovery(boolean receipt,Object[] before,Object[] current){
        if(receipt)return Recovery.ALREADY_APPLIED;
        return Arrays.equals(before,current)?Recovery.APPLY:Recovery.CONFLICT;
    }
    static <T> boolean conserves(java.util.List<T> before,java.util.List<T> after,java.util.function.ToLongFunction<T> amount,java.util.function.BiPredicate<T,T> same){
        var representatives=new java.util.ArrayList<T>();
        for(var list:java.util.List.of(before,after))for(T item:list)if(item!=null&&amount.applyAsLong(item)>0&&representatives.stream().noneMatch(x->same.test(x,item)))representatives.add(item);
        for(T item:representatives){
            long old=0,next=0;
            for(T x:before)if(x!=null&&amount.applyAsLong(x)>0&&same.test(item,x))old=Math.addExact(old,amount.applyAsLong(x));
            for(T x:after)if(x!=null&&amount.applyAsLong(x)>0&&same.test(item,x))next=Math.addExact(next,amount.applyAsLong(x));
            if(old!=next)return false;
        }
        return true;
    }
    static int capacity(int level){return level<=1?1:Math.min(54,(Math.min(7,level)-1)*9);}
    static boolean allowedChestClick(boolean member,boolean holder,boolean normalClick,boolean emptyCursor){
        return member&&holder&&normalClick&&emptyCursor;
    }
}
