import java.util.List;

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
