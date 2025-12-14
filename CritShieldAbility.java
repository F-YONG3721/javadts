import java.util.List;

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
