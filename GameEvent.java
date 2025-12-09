interface GameEvent // 事件型別定義
{
    String getEventType();
}

class OnCritEvent implements GameEvent {
    public Entity attacker;
    public OnCritEvent(Entity attacker) {
        this.attacker = attacker;
    }
    @Override
    public String getEventType() { return "OnCrit"; }
}

class TickEvent implements GameEvent {
    @Override
    public String getEventType() { return "Tick"; }
}

class OnDamageTakenEvent implements GameEvent {
    public Entity defender;
    public double damage;
    public OnDamageTakenEvent(Entity defender, double damage) {
        this.defender = defender;
        this.damage = damage;
    }
    @Override
    public String getEventType() { return "OnDamageTaken"; }
}
