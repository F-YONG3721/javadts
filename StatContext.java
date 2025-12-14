import java.util.Map;
import java.util.HashMap;

// 狀態上下文
class StatContext {
    public Map<String, Double> attributesMap = new HashMap<>();
    
    public void setAtk(double value) {
        attributesMap.put("ATK", value);
    }
    
    public void setShield(double value) {
        attributesMap.put("SHIELD", value);
    }
}
