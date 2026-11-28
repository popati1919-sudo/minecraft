package ca.larperscoin.smp;

import java.util.Arrays;
import java.util.Random;

/** Executable regression checks; no external test dependency or live server required. */
public final class AntiDuplicationTest {
    private static int checks;
    private static void check(boolean condition,String name){
        checks++;if(!condition)throw new AssertionError(name);
    }
    private static void reject(Runnable action,String name){
        try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException|ArithmeticException expected){checks++;}
    }
    private static void receiptTests()throws Exception{
        var dir=java.nio.file.Files.createTempDirectory("larry-receipt-");
        var path=dir.resolve("player.dat");
        try{
            String key="larperscoin:trade_123";
            for(int mode=0;mode<3;mode++){
                try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(path)))){
                    out.writeByte(10);out.writeUTF("");
                    out.writeByte(10);out.writeUTF(mode==1?"NotPlayerPdc":"BukkitValues");
                    out.writeByte(1);out.writeUTF(key);out.writeByte(mode==2?0:1);
                    out.writeByte(0);out.writeByte(0);
                }
                check(PlayerReceipt.exists(path,key)==(mode==0),"disk receipt "+mode);
            }
            java.nio.file.Files.writeString(path,"invalid");
            try{PlayerReceipt.exists(path,key);throw new AssertionError("malformed NBT accepted");}
            catch(java.io.IOException expected){checks++;}
        }finally{java.nio.file.Files.deleteIfExists(path);java.nio.file.Files.deleteIfExists(dir);}
    }
    private static void recipeTests(){
        var prices=java.util.Map.of("log",0.20,"plank",0.20,"stick",0.20);
        var rules=java.util.List.of(new RecipeEconomy.Rule("plank",4,java.util.List.of(java.util.List.of("log"))),new RecipeEconomy.Rule("stick",4,java.util.List.of(java.util.List.of("plank"),java.util.List.of("plank"))));
        var caps=RecipeEconomy.ceilings(prices,rules);
        check(caps.get("plank")*4<=prices.get("log"),"no direct recipe arbitrage");
        check(caps.get("stick")*8<=prices.get("log"),"no chained recipe arbitrage");
        check(RecipeEconomy.ceilings(prices,java.util.List.of(new RecipeEconomy.Rule("plank",4,java.util.List.of(java.util.List.of("missing"))))).get("plank")>0,"unknown ingredients not free");
    }
    private record Stack(String type,String metadata,int count){}
    private static void itemTests(){
        var before=java.util.List.of(new Stack("diamond","plain",32),new Stack("book","enchanted",1));
        var valid=java.util.List.of(new Stack("diamond","plain",16),new Stack("diamond","plain",16),new Stack("book","enchanted",1));
        var duplicate=java.util.List.of(new Stack("diamond","plain",64),new Stack("book","enchanted",1));
        var missing=java.util.List.of(new Stack("diamond","plain",31),new Stack("book","enchanted",1));
        var changed=java.util.List.of(new Stack("diamond","plain",32),new Stack("book","plain",1));
        java.util.function.BiPredicate<Stack,Stack> similar=(a,b)->a.type.equals(b.type)&&a.metadata.equals(b.metadata);
        check(AntiDuplication.conserves(before,valid,Stack::count,similar),"stack splitting preserves quantity");
        check(!AntiDuplication.conserves(before,duplicate,Stack::count,similar),"reject duplicated stack");
        check(!AntiDuplication.conserves(before,missing,Stack::count,similar),"reject missing item");
        check(!AntiDuplication.conserves(before,changed,Stack::count,similar),"reject changed metadata");
    }
    public static void main(String[] args)throws Exception{
        receiptTests();recipeTests();itemTests();
        check(AntiDuplication.units(0.1+0.2)==3000,"floating point rounding");
        check(AntiDuplication.units(AntiDuplication.add(1.0001,-0.0001))==10000,"exact 4 decimal debit");
        reject(()->AntiDuplication.add(0,-0.0001),"negative balances");
        reject(()->AntiDuplication.units(Double.NaN),"NaN");
        reject(()->AntiDuplication.units(Double.POSITIVE_INFINITY),"infinity");
        reject(()->AntiDuplication.add(AntiDuplication.amount(AntiDuplication.MAX_UNITS),0.0001),"balance cap");
        Random rng=new Random(24119L);
        for(int i=0;i<10000;i++){
            long a=rng.nextLong(10_000_000),b=rng.nextLong(10_000_000),x=rng.nextLong(a+1),y=rng.nextLong(b+1);
            double nextA=AntiDuplication.add(AntiDuplication.add(AntiDuplication.amount(a),-AntiDuplication.amount(x)),AntiDuplication.amount(y));
            double nextB=AntiDuplication.add(AntiDuplication.add(AntiDuplication.amount(b),-AntiDuplication.amount(y)),AntiDuplication.amount(x));
            check(AntiDuplication.units(nextA)+AntiDuplication.units(nextB)==a+b,"trade conservation "+i);
        }
        Object[] before={"stone:64",null,"iron:3"},after={null,"diamond:2","iron:3"},foreign={"dirt:5",null,null};
        check(AntiDuplication.recovery(false,before,before.clone())==AntiDuplication.Recovery.APPLY,"crash before delivery");
        check(AntiDuplication.recovery(true,before,after)==AntiDuplication.Recovery.ALREADY_APPLIED,"crash after delivery");
        check(AntiDuplication.recovery(true,before,foreign)==AntiDuplication.Recovery.ALREADY_APPLIED,"no replay after later activity");
        check(AntiDuplication.recovery(false,before,foreign)==AntiDuplication.Recovery.CONFLICT,"do not overwrite unrelated inventory");
        check(AntiDuplication.recovery(false,before,after)==AntiDuplication.Recovery.CONFLICT,"missing receipt, fail closed");
        int[] slots={1,9,18,27,36,45,54};
        for(int i=0;i<slots.length;i++)check(AntiDuplication.capacity(i+1)==slots[i],"capacity "+(i+1));
        check(AntiDuplication.capacity(100)==54,"invalid capacity capped");
        for(int mask=0;mask<16;mask++){
            boolean member=(mask&1)!=0,holder=(mask&2)!=0,normal=(mask&4)!=0,cursor=(mask&8)!=0;
            check(AntiDuplication.allowedChestClick(member,holder,normal,cursor)==(mask==15),"chest permission "+mask);
        }
        // Economic-unit tests cover the production rounding helper; they are not a live Paper inventory test.
        check(AntiDuplication.units(AntiDuplication.amount(AntiDuplication.MAX_UNITS-1))==AntiDuplication.MAX_UNITS-1,"upper-range round trip");
        System.out.println("AntiDuplicationTest: "+checks+" assertions passed");
    }
}
