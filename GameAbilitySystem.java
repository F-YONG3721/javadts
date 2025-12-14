import java.util.*;

// 遊戲模式
enum GameMode {
    PVE, PVP
}

// 主程式
public class GameAbilitySystem {
    public static void main(String[] args) {
        System.out.println("=== 遊戲能力系統演進需求測試 ===\n");
        
        // 測試 1: 暴擊觸盾（無冷卻）
        test1_CritShieldNoCooldown();
        
        // 測試 2: 暴擊觸盾（有冷卻）
        test2_CritShieldWithCooldown();
        
        // 測試 3: 光環系統
        test3_AuraSystem();
        
        // 測試 4: 互斥規則
        test4_MutualExclusion();
        
        // 測試 5: 模式化（PVP 模式）
        test5_PVPMode();
    }
    
    static void test1_CritShieldNoCooldown() {
        System.out.println("測試 1: 暴擊觸盾（無冷卻）");
        
        Entity hero = new Entity("hero1", 100, 50, 0, 0);
        AbilitySystem system = new AbilitySystem();
        system.registerAbility(new CritShieldAbility(100, 0)); // 無冷卻
        BattleLog log = new BattleLog();
        
        // 連續兩次暴擊事件
        system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
        system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
        
        StatContext stats = system.computeStats(hero);
        System.out.println("護盾值: " + stats.attributesMap.get("SHIELD"));
        System.out.println("期望: 200(100 + 100)");
        log.print();
        System.out.println();
    }
    
    static void test2_CritShieldWithCooldown() {
        System.out.println("測試 2: 暴擊觸盾（有冷卻 10s)");
        
        Entity hero = new Entity("hero2", 100, 50, 0, 0);
        AbilitySystem system = new AbilitySystem();
        system.registerAbility(new CritShieldAbility(100, 10000)); // 10秒冷卻
        BattleLog log = new BattleLog();
        
        // 連續兩次暴擊事件
        system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
        try {
            Thread.sleep(100); // 短暫延遲
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
        
        StatContext stats = system.computeStats(hero);
        System.out.println("護盾值: " + stats.attributesMap.get("SHIELD"));
        System.out.println("期望: 100（第二次在冷卻內被阻擋）");
        log.print();
        System.out.println();
    }
    
    static void test3_AuraSystem() {
        System.out.println("測試 3: 光環系統（隊友在 5m 內獲得 +10% 攻擊）");
        
        Entity supporter = new Entity("supporter", 100, 50, 0, 0);
        Entity ally1 = new Entity("ally1", 100, 50, 3, 0);
        Entity ally2 = new Entity("ally2", 100, 50, 6, 0);
        
        AbilitySystem system = new AbilitySystem();
        system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));
        BattleLog log = new BattleLog();
        
        List<Entity> entities = Arrays.asList(supporter, ally1, ally2);
        system.triggerEvent(new TickEvent(), supporter, entities, log);
        
        StatContext stats1 = system.computeStats(ally1);
        StatContext stats2 = system.computeStats(ally2);
        
        System.out.println("Ally1 (距離 3m) 攻擊: " + stats1.attributesMap.get("ATK"));
        System.out.println("期望: 110（100 * 1.1）");
        System.out.println("Ally2 (距離 6m) 攻擊: " + stats2.attributesMap.get("ATK"));
        System.out.println("期望: 100（超出範圍）");
        log.print();
        System.out.println();
    }
    
    static void test4_MutualExclusion() {
        System.out.println("測試 4: 互斥規則（兩個同類光環只取最大值）");
        
        Entity hero = new Entity("hero", 100, 50, 0, 0);
        
        // 添加兩個不同的 ATK 光環
        Buff buff1 = new Buff("AURA_ATK_10", 5000);
        buff1.modifiers.add(new StatModifier("ATK_UP", 0.10, 5000, "AURA_ATK_10"));
        
        Buff buff2 = new Buff("AURA_ATK_15", 5000);
        buff2.modifiers.add(new StatModifier("ATK_UP", 0.15, 5000, "AURA_ATK_15"));
        
        hero.addBuff(buff1);
        hero.addBuff(buff2);
        
        AbilitySystem system = new AbilitySystem();
        StatContext stats = system.computeStats(hero);
        
        System.out.println("攻擊力（+10% 與 +15% 的最大值）: " + stats.attributesMap.get("ATK"));
        System.out.println("期望: 115（100 * 1.15）");
        System.out.println();
    }
    
    static void test5_PVPMode() {
        System.out.println("測試 5: PVP 模式（光環上限 5%）");
        
        Entity supporter = new Entity("supporter", 100, 50, 0, 0);
        Entity ally = new Entity("ally", 100, 50, 3, 0);
        
        AbilitySystem system = new AbilitySystem();
        system.setGameMode(GameMode.PVP); // 設置 PVP 模式
        system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));
        BattleLog log = new BattleLog();
        
        List<Entity> entities = Arrays.asList(supporter, ally);
        system.triggerEvent(new TickEvent(), supporter, entities, log);
        
        StatContext stats = system.computeStats(ally);
        
        System.out.println("PVP 模式下盟友攻擊力（10% 被限制到 5%）: " + stats.attributesMap.get("ATK"));
        System.out.println("期望: 105（100 * 1.05）");
        log.print();
        System.out.println();
    }
}
