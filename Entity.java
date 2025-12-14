import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

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
