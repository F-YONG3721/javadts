import java.util.List;
import java.util.ArrayList;

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
