import java.util.ArrayList;
import java.util.List;

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
