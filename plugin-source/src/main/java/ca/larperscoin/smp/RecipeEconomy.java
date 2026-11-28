package ca.larperscoin.smp;

import java.util.*;

/** Conservative price ceilings for recipes with known, shop-purchasable ingredients. */
final class RecipeEconomy {
    record Rule(String output,int quantity,List<List<String>> ingredients){}
    private RecipeEconomy(){}
    static Map<String,Double> ceilings(Map<String,Double> purchase,List<Rule> rules){
        Map<String,Double> cost=new HashMap<>(purchase);
        // Lower costs along multi-stage recipes until no additional reduction is found.
        for(int round=0;round<128;round++){
            boolean changed=false;
            for(Rule r:rules){
                if(r.quantity<=0||r.ingredients.isEmpty()||!cost.containsKey(r.output))continue;
                double sum=0;
                for(var choices:r.ingredients){
                    double best=Double.POSITIVE_INFINITY;
                    for(String item:choices)best=Math.min(best,cost.getOrDefault(item,Double.POSITIVE_INFINITY));
                    sum+=best;
                }
                double candidate=sum/r.quantity;
                if(candidate>=0&&candidate<cost.get(r.output)-1e-12){cost.put(r.output,candidate);changed=true;}
            }
            if(!changed)break;
        }
        Map<String,Double> limits=new HashMap<>();
        cost.forEach((k,v)->limits.put(k,Math.floor(Math.max(0,v)*0.98*10000)/10000));
        return limits;
    }
}
