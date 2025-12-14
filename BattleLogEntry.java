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
