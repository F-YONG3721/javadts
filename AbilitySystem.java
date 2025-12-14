import java.util.ArrayList;
import java.util.List;

// 能力系統管理器
class AbilitySystem {
    private List<Ability> abilities = new ArrayList<>();
    private GameMode mode = GameMode.PVE;
    
    public void registerAbility(Ability ability) {
        abilities.add(ability);
    }
    
    public void setGameMode(GameMode mode) {
        this.mode = mode;
    }
    
    public void triggerEvent(GameEvent event, Entity entity, List<Entity> allEntities, BattleLog log) {
        for (Ability ability : abilities) {
            ability.trigger(entity, event, allEntities, mode, log);
        }
    }
    
    public StatContext computeStats(Entity entity) {
        StatContext context = new StatContext();
        entity.cleanupExpired();
        context.setAtk(entity.getModifiedAtk(mode));
        context.setShield(entity.getTotalShield());
        return context;
    }
}
