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
