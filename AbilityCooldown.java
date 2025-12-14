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
