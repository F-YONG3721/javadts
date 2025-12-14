import java.util.List;

// 能力策略基類
abstract class Ability {
    public String name;
    
    public Ability(String name) {
        this.name = name;
    }
    
    abstract void trigger(Entity entity, GameEvent event, List<Entity> allEntities, GameMode mode, BattleLog log);
}
