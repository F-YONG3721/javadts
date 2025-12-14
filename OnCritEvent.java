class OnCritEvent implements GameEvent {
    public Entity attacker;
    public OnCritEvent(Entity attacker) {
        this.attacker = attacker;
    }
    @Override
    public String getEventType() { return "OnCrit"; }
}
