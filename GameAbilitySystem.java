import java.util.*;

class Shield // 護盾
{
    public String abilityName;
    public double value;
    public long expirationTime;
    
    public Shield(String abilityName, double value, long duration) {
        this.abilityName = abilityName;
        this.value = value;
        this.expirationTime = System.currentTimeMillis() + duration;
    }
    
    public boolean isExpired() {
        // 護盾永不過期（使用 Long.MAX_VALUE）
        return false;
    }
}

// Buff 物件
class Buff {
    public String name;
    public List<StatModifier> modifiers;
    public long expirationTime;
    public int stackCount;
    
    public Buff(String name, long duration) {
        this.name = name;
        this.modifiers = new ArrayList<>();
        this.expirationTime = System.currentTimeMillis() + duration;
        this.stackCount = 1;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}

// 能力冷卻狀態
class AbilityCooldown {
    public String abilityName;
    public long lastTriggeredTime;
    public long cooldownDuration;
    
    public AbilityCooldown(String abilityName, long cooldownDuration) {
        this.abilityName = abilityName;
        this.cooldownDuration = cooldownDuration;
        this.lastTriggeredTime = 0;
    }
    
    public boolean isOnCooldown() {
        return System.currentTimeMillis() - lastTriggeredTime < cooldownDuration;
    }
    
    public void trigger() {
        this.lastTriggeredTime = System.currentTimeMillis();
    }
}

// 遊戲實體
class Entity {
    public String id;
    public double baseAtk;
    public double baseDef;
    public double x, y; // 位置
    
    public List<Shield> shields = new ArrayList<>();
    public List<Buff> buffs = new ArrayList<>();
    public Map<String, AbilityCooldown> cooldowns = new HashMap<>();
    
    public Entity(String id, double baseAtk, double baseDef, double x, double y) {
        this.id = id;
        this.baseAtk = baseAtk;
        this.baseDef = baseDef;
        this.x = x;
        this.y = y;
    }
    
    public double getTotalShield() {
        double total = 0;
        for (Shield shield : shields) {
            if (!shield.isExpired()) {
                total += shield.value;
            }
        }
        return total;
    }
    
    public double getModifiedAtk(GameMode mode) {
        double total = baseAtk;
        List<Buff> validBuffs = new ArrayList<>();
        
        for (Buff buff : buffs) {
            if (!buff.isExpired()) {
                validBuffs.add(buff);
            }
        }
        
        // 處理互斥（同類修飾類型只取最大值）
        Map<String, Double> maxModifiersByType = new HashMap<>();
        for (Buff buff : validBuffs) {
            for (StatModifier mod : buff.modifiers) {
                if (mod.type.equals("ATK_UP")) {
                    double appliedValue = mod.value;
                    if (mode == GameMode.PVP) {
                        appliedValue = Math.min(appliedValue, 0.05); // PVP 模式上限 5%
                    }
                    // 同一類型修飾只取最大值（互斥規則）
                    String key = "ATK_UP"; // 按修飾類型分組
                    maxModifiersByType.put(key, Math.max(
                        maxModifiersByType.getOrDefault(key, 0.0),
                        appliedValue
                    ));
                }
            }
        }
        
        // 應用所有修飾
        for (double modifier : maxModifiersByType.values()) {
            total *= (1 + modifier);
        }
        
        return total;
    }
    
    public void addBuff(Buff buff) {
        buffs.add(buff);
    }
    
    public void addShield(Shield shield) {
        shields.add(shield);
    }
    
    public void cleanupExpired() {
        shields.removeIf(Shield::isExpired);
        buffs.removeIf(Buff::isExpired);
    }
}

// 遊戲模式
enum GameMode {
    PVE, PVP
}

// 能力策略基類
abstract class Ability {
    public String name;
    
    public Ability(String name) {
        this.name = name;
    }
    
    abstract void trigger(Entity entity, GameEvent event, List<Entity> allEntities, GameMode mode, BattleLog log);
}

// 暴擊觸盾能力
class CritShieldAbility extends Ability {
    private double shieldValue;
    private long cooldownDuration;
    
    public CritShieldAbility(double shieldValue, long cooldownDuration) {
        super("CRIT_SHIELD");
        this.shieldValue = shieldValue;
        this.cooldownDuration = cooldownDuration;
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, List<Entity> allEntities, GameMode mode, BattleLog log) {
        if (!(event instanceof OnCritEvent)) return;
        
        OnCritEvent critEvent = (OnCritEvent) event;
        if (!critEvent.attacker.equals(entity)) return;
        
        AbilityCooldown cooldown = entity.cooldowns.computeIfAbsent(
            this.name,
            k -> new AbilityCooldown(this.name, cooldownDuration)
        );
        
        if (cooldownDuration > 0 && cooldown.isOnCooldown()) {
            log.addEntry(entity.id, event.getEventType(), this.name, "BLOCKED_BY_COOLDOWN", "");
            return;
        }
        
        // 護盾長期保存（不會過期）
        Shield shield = new Shield(this.name, shieldValue, Long.MAX_VALUE);
        entity.addShield(shield);
        cooldown.trigger();
        
        log.addEntry(entity.id, event.getEventType(), this.name, "EFFECT_APPLIED", 
            String.format("shield:%.0f", shieldValue));
    }
}

// 光環能力
class AuraAbility extends Ability {
    private double atkBonus; // 百分比
    private double range;
    private long duration;
    
    public AuraAbility(String name, double atkBonus, double range, long duration) {
        super(name);
        this.atkBonus = atkBonus;
        this.range = range;
        this.duration = duration;
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, List<Entity> allEntities, GameMode mode, BattleLog log) {
        if (!(event instanceof TickEvent)) return;
        
        // 找出範圍內的隊友
        for (Entity other : allEntities) {
            if (other.equals(entity)) continue;
            
            double distance = Math.sqrt(
                Math.pow(entity.x - other.x, 2) + 
                Math.pow(entity.y - other.y, 2)
            );
            
            if (distance <= range) {
                Buff buff = new Buff(this.name, duration);
                StatModifier mod = new StatModifier("ATK_UP", atkBonus, duration, this.name);
                buff.modifiers.add(mod);
                other.addBuff(buff);
                
                log.addEntry(other.id, event.getEventType(), this.name, "BUFF_APPLIED", 
                    String.format("atk_bonus:%.0f%%", atkBonus * 100));
            }
        }
    }
}

// 戰鬥日誌項目
class BattleLogEntry {
    public long time;
    public String entityId;
    public String event;
    public String ability;
    public String result;
    public String details;
    
    public BattleLogEntry(long time, String entityId, String event, String ability, String result, String details) {
        this.time = time;
        this.entityId = entityId;
        this.event = event;
        this.ability = ability;
        this.result = result;
        this.details = details;
    }
    
    @Override
    public String toString() {
        return String.format("t:%d, entity:%s, evt:%s, ability:%s, result:%s, detail:%s",
            time, entityId, event, ability, result, details);
    }
}

// 戰鬥日誌
class BattleLog {
    private List<BattleLogEntry> entries = new ArrayList<>();
    private long startTime = System.currentTimeMillis();
    
    public void addEntry(String entityId, String event, String ability, String result, String details) {
        long relativeTime = System.currentTimeMillis() - startTime;
        entries.add(new BattleLogEntry(relativeTime, entityId, event, ability, result, details));
    }
    
    public void print() {
        System.out.println("=== BATTLE LOG ===");
        for (BattleLogEntry entry : entries) {
            System.out.println(entry);
        }
        System.out.println("==================");
    }
    
    public List<BattleLogEntry> getEntries() {
        return entries;
    }
}

// 狀態上下文
class StatContext {
    public Map<String, Double> attributesMap = new HashMap<>();
    
    public void setAtk(double value) {
        attributesMap.put("ATK", value);
    }
    
    public void setShield(double value) {
        attributesMap.put("SHIELD", value);
    }
}

// 能力系統管理器
class AbilitySystem {
    private List<Ability> abilities = new ArrayList<>();
    private GameMode mode = GameMode.PVE;
    
    public void registerAbility(Ability ability) {
        abilities.add(ability);
    }
    
    public void setGameMode(GameMode mode) {
        this.mode = mode;
    }
    
    public void triggerEvent(GameEvent event, Entity entity, List<Entity> allEntities, BattleLog log) {
        for (Ability ability : abilities) {
            ability.trigger(entity, event, allEntities, mode, log);
        }
    }
    
    public StatContext computeStats(Entity entity) {
        StatContext context = new StatContext();
        entity.cleanupExpired();
        context.setAtk(entity.getModifiedAtk(mode));
        context.setShield(entity.getTotalShield());
        return context;
    }
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
