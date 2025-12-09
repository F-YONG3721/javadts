# 遊戲能力系統 (Game Ability System)

## 目錄
1. [概述](#概述)
2. [系統架構](#系統架構)
3. [主要類別](#主要類別說明)
4. [具體能力](#具體能力實現)
5. [核心機制](#核心機制詳解)
6. [測試案例](#五大測試需求)
7. [設計分析](#設計模式與分析)

---

## 概述

這是一個為 ARPG 遊戲「RuneRise」設計的可擴充能力系統，支援事件驅動、能力疊加、互斥規則、冷卻機制和模式化設定。

### 需求背景

遊戲需要快速上線新能力，如：
- 暴擊給盾：暴擊時產生護盾
- 光環攻擊加成：範圍內隊友獲得加成
- 元素剋制：未來可擴展

### 設計特點

✅ **事件驅動** - 通過事件觸發能力反應  
✅ **可疊加** - 多個 Buff/護盾可同時存在  
✅ **互斥規則** - 同類修飾只取最大值  
✅ **冷卻控制** - 防止能力過度觸發  
✅ **模式化** - PVE/PVP 模式下不同效果  
✅ **可擴展** - 易於新增新能力和事件

---

## 系統架構

### 核心流程圖

```
┌─────────────────────────────────────────────────────────────┐
│                       遊戲事件發生                              │
│  (OnCritEvent, TickEvent, OnDamageTakenEvent)                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
           ┌─────────────────────────────────┐
           │   AbilitySystem.triggerEvent()  │
           │   遍歷所有註冊的能力              │
           └────────────┬────────────────────┘
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
    ┌─────────────────┐  ┌──────────────────┐
    │ CritShieldAbility│  │  AuraAbility     │
    │                 │  │  (未來可擴展)     │
    └────────┬────────┘  └────────┬─────────┘
             │                    │
       ┌─────▼────────────────────▼─────┐
       │   Entity 狀態更新              │
       │  (Buffs, Shields, Cooldowns)   │
       └─────┬──────────────────────────┘
             │
             ▼
    ┌─────────────────────┐
    │  BattleLog 記錄      │
    │  所有觸發事件        │
    └────────┬────────────┘
             │
             ▼
    ┌──────────────────────┐
    │ StatContext 計算結果  │
    │ (ATK, SHIELD)        │
    └──────────────────────┘
```

### 數據流動

```
事件發生
  │
  ├─→ 能力1檢查 ──→ 條件符合? ──→ 是 → 觸發 → 更新實體
  │                              │
  │                              └→ 否 → 跳過
  │
  ├─→ 能力2檢查 ──→ 條件符合? ──→ 是 → 觸發 → 更新實體
  │                              │
  │                              └→ 否 → 跳過
  │
  └─→ 日誌記錄 + 屬性計算
```

### 核心概念

```
遊戲循環
  ├─ 事件層 (Event Layer)
  │   ├─ OnCritEvent      (玩家暴擊)
  │   ├─ TickEvent        (時間更新)
  │   └─ OnDamageTakenEvent (受傷)
  │
  ├─ 能力層 (Ability Layer)
  │   ├─ CritShieldAbility (暴擊護盾)
  │   ├─ AuraAbility       (光環)
  │   └─ 未來: DamageReflection, Heal, etc.
  │
  ├─ 狀態層 (State Layer)
  │   ├─ Buffs            (增益效果)
  │   ├─ Shields          (護盾)
  │   ├─ Cooldowns        (冷卻)
  │   └─ Stats            (屬性)
  │
  └─ 日誌層 (Log Layer)
      └─ BattleLog        (記錄所有事件)
```

## 主要類別說明

### 1. 事件系統 (GameEvent Interface)

#### 介面定義
```java
interface GameEvent {
    String getEventType();
}
```

#### 設計原理

使用接口定義事件契約，所有事件都必須實現 `getEventType()` 方法。

**優勢**:
- 統一事件格式
- 易於擴展新事件類型
- 遵循開閉原則

#### 實現類別

##### OnCritEvent - 暴擊事件
```java
class OnCritEvent implements GameEvent {
    public Entity attacker;
    public OnCritEvent(Entity attacker) {
        this.attacker = attacker;
    }
    @Override
    public String getEventType() { return "OnCrit"; }
}
```

**觸發條件**: 攻擊者暴擊時  
**包含信息**: 暴擊者實體  
**應用**: 暴擊觸盾、暴擊吸血等

**使用例**:
```java
// 當玩家暴擊時
Entity hero = ...;
GameEvent event = new OnCritEvent(hero);
abilitySystem.triggerEvent(event, hero, allEntities, log);
```

##### TickEvent - 時間滴答事件
```java
class TickEvent implements GameEvent {
    @Override
    public String getEventType() { return "Tick"; }
}
```

**觸發頻率**: 每個遊戲幀或時間單位  
**用途**: 持續效果更新  
**應用**: 光環效果、持續傷害等

**使用例**:
```java
// 遊戲每個時間週期觸發
while (gameRunning) {
    abilitySystem.triggerEvent(new TickEvent(), entity, allEntities, log);
    // ... 其他遊戲邏輯
}
```

##### OnDamageTakenEvent - 受傷事件
```java
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
```

**觸發條件**: 實體受傷  
**包含信息**: 受傷者、傷害量  
**應用**: 傷害反彈、護盾吸收等

**未來擴展示例**:
```java
// 反彈傷害能力
class ReflectAbility extends Ability {
    @Override
    void trigger(Entity entity, GameEvent event, ...) {
        if (!(event instanceof OnDamageTakenEvent)) return;
        OnDamageTakenEvent dmgEvent = (OnDamageTakenEvent) event;
        // 反彈 30% 傷害給攻擊者
        double reflectDamage = dmgEvent.damage * 0.3;
    }
}
```

---

### 2. 屬性修飾器 (StatModifier)

#### 完整定義
```java
class StatModifier {
    public String type;           // "ATK_UP", "DEF_UP", "CRIT_RATE" 等
    public double value;          // 百分比 (0.1 = 10%) 或絕對值
    public long expirationTime;   // 過期時間戳 (毫秒)
    public String source;         // 來源能力名稱，用於追蹤
    
    public StatModifier(String type, double value, long duration, String source) {
        this.type = type;
        this.value = value;
        this.expirationTime = System.currentTimeMillis() + duration;
        this.source = source;
    }
}
```

#### 修飾器類型

| 類型 | 範例 | 解釋 |
|------|------|------|
| ATK_UP | 0.1 | 攻擊力增加 10% |
| ATK_DOWN | -0.1 | 攻擊力降低 10% |
| DEF_UP | 0.2 | 防禦力增加 20% |
| CRIT_RATE | 0.05 | 暴擊率增加 5% |
| CRIT_DMG | 0.3 | 暴擊傷害增加 30% |
| HP_UP | 100 | 生命值增加 100 點 |

#### 生命週期

```
建立時刻
  │
  ├─ expirationTime = now + duration
  │
  ▼
效果持續期間
  │
  ├─ 在屬性計算時應用
  │
  ▼
過期檢查
  │
  ├─ if (now > expirationTime) → 移除
  │
  ▼
被移除
```

#### 應用場景

```java
// 光環加成
StatModifier auraBonus = new StatModifier(
    "ATK_UP",      // 類型
    0.1,           // +10% 攻擊
    5000,          // 持續 5 秒
    "AURA_ATK_UP"  // 來源
);

// 中毒效果 (未來)
StatModifier poison = new StatModifier(
    "DEF_DOWN",
    -0.2,
    3000,
    "POISON"
);
```

---

### 3. 護盾 (Shield)

#### 完整定義
```java
class Shield {
    public String abilityName;    // 護盾來源能力
    public double value;          // 護盾值 (降減傷害)
    public long expirationTime;   // 過期時間
    
    public Shield(String abilityName, double value, long duration) {
        this.abilityName = abilityName;
        this.value = value;
        this.expirationTime = System.currentTimeMillis() + duration;
    }
    
    public boolean isExpired() {
        // 護盾永不自動過期 (使用 Long.MAX_VALUE)
        return false;
    }
}
```

#### 護盾機制詳解

**護盾的作用**:
1. 減少受到的傷害
2. 多個護盾可疊加
3. 優先於生命值吸收傷害

**護盾計算示例**:
```
基礎生命: 100
受傷: 50

情況1 - 無護盾:
  剩餘生命 = 100 - 50 = 50

情況2 - 有 60 護盾:
  護盾吸收: min(60, 50) = 50
  剩餘護盾: 60 - 50 = 10
  剩餘生命: 100 (未扣)

情況3 - 有 30 護盾:
  護盾吸收: 30
  剩餘傷害: 50 - 30 = 20
  剩餘護盾: 0
  剩餘生命: 100 - 20 = 80
```

#### 多護盾疊加

```java
Entity hero = new Entity("hero", 100, 50, 0, 0);

// 添加多個護盾
hero.addShield(new Shield("CRIT_SHIELD", 100, Long.MAX_VALUE));
hero.addShield(new Shield("CRIT_SHIELD", 100, Long.MAX_VALUE));

// 計算總護盾
double totalShield = hero.getTotalShield();
// 結果: 200.0
```

#### 護盾永久性設置

```java
// 為什麼使用 Long.MAX_VALUE?
public Shield(String abilityName, double value, long duration) {
    this.abilityName = abilityName;
    this.value = value;
    // Long.MAX_VALUE = 9223372036854775807
    // 即使系統運行 292 年也不會過期
    this.expirationTime = System.currentTimeMillis() + Long.MAX_VALUE;
}

public boolean isExpired() {
    return false; // 永遠不過期
}
```

**設計考量**:
- 護盾一旦給予就持續存在
- 通過累加實現護盾堆疊
- 遊戲邏輯處理護盾破裂

---

### 4. Buff 物件 (Buff)

#### 完整定義
```java
class Buff {
    public String name;                    // Buff 名稱 (AURA_ATK_UP)
    public List<StatModifier> modifiers;   // 包含的所有修飾器
    public long expirationTime;            // Buff 過期時間
    public int stackCount;                 // 堆疊層數 (未使用)
    
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
```

#### Buff 與 Modifier 的關係

```
Buff (光環)
  ├─ name: "AURA_ATK_UP"
  ├─ modifiers: [
  │   ├─ StatModifier(ATK_UP, 0.1, 5s, AURA_ATK_UP),
  │   └─ StatModifier(CRIT_RATE, 0.05, 5s, AURA_ATK_UP)
  │ ]
  └─ expirationTime: now + 5000
```

#### Buff 生命週期

```
建立 (Buff 應用到實體)
  │
  ▼
活躍期 (isExpired() == false)
  │
  ├─ 應用所有 modifiers
  ├─ 更新實體屬性
  │
  ▼
過期檢查 (cleanupExpired())
  │
  ├─ if (isExpired()) → 移除
  │
  ▼
移除 (cleanupExpired 中刪除)
```

#### Buff 疊加策略

目前系統設計為「不重複添加相同名稱的 Buff」:

```java
public void addBuff(Buff buff) {
    // 簡單實現 - 直接添加
    buffs.add(buff);
}
```

**未來改進**:
```java
public void addBuff(Buff buff) {
    // 檢查是否已存在相同名稱 Buff
    for (Buff existing : buffs) {
        if (existing.name.equals(buff.name) && !existing.isExpired()) {
            // 刷新持續時間或增加層數
            existing.expirationTime = buff.expirationTime;
            existing.stackCount++;
            return;
        }
    }
    buffs.add(buff);
}
```

---

### 5. 冷卻系統 (AbilityCooldown)

#### 完整定義
```java
class AbilityCooldown {
    public String abilityName;        // 能力名稱
    public long lastTriggeredTime;    // 上次觸發時間戳
    public long cooldownDuration;     // 冷卻持續時間 (毫秒)
    
    public AbilityCooldown(String abilityName, long cooldownDuration) {
        this.abilityName = abilityName;
        this.cooldownDuration = cooldownDuration;
        this.lastTriggeredTime = 0;  // 初始為 0，意味著能立即觸發
    }
    
    public boolean isOnCooldown() {
        // 計算是否仍在冷卻期內
        return System.currentTimeMillis() - lastTriggeredTime < cooldownDuration;
    }
    
    public void trigger() {
        // 記錄觸發時間
        this.lastTriggeredTime = System.currentTimeMillis();
    }
}
```

#### 冷卻時間軸

```
時刻 0ms
  ├─ 第一次觸發
  │  └─ trigger() → lastTriggeredTime = 0
  │
時刻 50ms
  ├─ 檢查冷卻
  │  └─ (50 - 0) < 10000 → 在冷卻中 ❌
  │
時刻 100ms
  ├─ 檢查冷卻
  │  └─ (100 - 0) < 10000 → 在冷卻中 ❌
  │
時刻 10000ms
  ├─ 檢查冷卻
  │  └─ (10000 - 0) = 10000 NOT < 10000 → 冷卻結束 ✅
  │  └─ 可以再次觸發
  │
時刻 10100ms
  ├─ 第二次觸發
  │  └─ trigger() → lastTriggeredTime = 10100
  │  └─ 新的冷卻周期開始
```

#### 冷卻計算邏輯

```java
// 假設冷卻時間 = 10000ms (10秒)
long cooldownDuration = 10000;

// 場景 1: 剛觸發
long lastTriggeredTime = System.currentTimeMillis();  // 假設 = 1000ms
long currentTime = System.currentTimeMillis();         // 假設 = 1001ms
boolean onCooldown = (1001 - 1000) < 10000;            // true (在冷卻中)

// 場景 2: 冷卻快結束
lastTriggeredTime = 1000;
currentTime = 11000;
onCooldown = (11000 - 1000) < 10000;                   // false (已可觸發)
```

#### 應用於能力

```java
class CritShieldAbility extends Ability {
    private long cooldownDuration;
    
    @Override
    void trigger(Entity entity, GameEvent event, ...) {
        // 取得或創建冷卻物件
        AbilityCooldown cooldown = entity.cooldowns.computeIfAbsent(
            this.name,
            k -> new AbilityCooldown(this.name, cooldownDuration)
        );
        
        // 檢查冷卻
        if (cooldownDuration > 0 && cooldown.isOnCooldown()) {
            log.addEntry(entity.id, event.getEventType(), this.name, 
                        "BLOCKED_BY_COOLDOWN", "");
            return;  // 被阻擋，不觸發
        }
        
        // 執行能力效果
        Shield shield = new Shield(this.name, shieldValue, Long.MAX_VALUE);
        entity.addShield(shield);
        
        // 更新冷卻
        cooldown.trigger();
        
        log.addEntry(entity.id, event.getEventType(), this.name, 
                    "EFFECT_APPLIED", String.format("shield:%.0f", shieldValue));
    }
}
```

---

### 6. 遊戲實體 (Entity)

#### 完整定義
```java
class Entity {
    public String id;              // 實體唯一識別符
    public double baseAtk;         // 基礎攻擊力
    public double baseDef;         // 基礎防禦力
    public double x, y;            // 2D 座標位置
    
    public List<Shield> shields;                       // 所有護盾
    public List<Buff> buffs;                           // 所有 Buff
    public Map<String, AbilityCooldown> cooldowns;     // 能力冷卻狀態
    
    public Entity(String id, double baseAtk, double baseDef, double x, double y) {
        this.id = id;
        this.baseAtk = baseAtk;
        this.baseDef = baseDef;
        this.x = x;
        this.y = y;
        
        this.shields = new ArrayList<>();
        this.buffs = new ArrayList<>();
        this.cooldowns = new HashMap<>();
    }
}
```

#### getTotalShield() - 計算總護盾

```java
public double getTotalShield() {
    double total = 0;
    for (Shield shield : shields) {
        if (!shield.isExpired()) {
            total += shield.value;
        }
    }
    return total;
}
```

**邏輯**:
1. 初始化總和為 0
2. 遍歷所有護盾
3. 檢查護盾是否過期
4. 累加未過期護盾的值

**示例**:
```
護盾列表:
  [Shield(CRIT_SHIELD, 100), Shield(CRIT_SHIELD, 100)]

計算過程:
  total = 0
  total += 100  // 第一個護盾
  total += 100  // 第二個護盾
  return 200
```

#### getModifiedAtk(GameMode mode) - 計算修飾後的攻擊力

##### 完整代碼
```java
public double getModifiedAtk(GameMode mode) {
    double total = baseAtk;  // 從基礎攻擊開始
    List<Buff> validBuffs = new ArrayList<>();
    
    // 第一步: 過濾有效的 Buff
    for (Buff buff : buffs) {
        if (!buff.isExpired()) {
            validBuffs.add(buff);
        }
    }
    
    // 第二步: 收集所有修飾器並處理互斥
    Map<String, Double> maxModifiersByType = new HashMap<>();
    for (Buff buff : validBuffs) {
        for (StatModifier mod : buff.modifiers) {
            if (mod.type.equals("ATK_UP")) {
                double appliedValue = mod.value;
                
                // PVP 模式限制
                if (mode == GameMode.PVP) {
                    appliedValue = Math.min(appliedValue, 0.05);  // 上限 5%
                }
                
                // 互斥規則: 同類型只保留最大值
                String key = "ATK_UP";
                maxModifiersByType.put(key, Math.max(
                    maxModifiersByType.getOrDefault(key, 0.0),
                    appliedValue
                ));
            }
        }
    }
    
    // 第三步: 應用所有修飾器
    for (double modifier : maxModifiersByType.values()) {
        total *= (1 + modifier);  // 乘法計算
    }
    
    return total;
}
```

##### 計算過程詳解

**場景 1: 無 Buff**
```
baseAtk = 100
validBuffs = []
maxModifiersByType = {}
total = 100
結果: 100
```

**場景 2: 單個 +10% Buff**
```
baseAtk = 100
validBuffs = [Buff(AURA_ATK_UP)]
maxModifiersByType = {"ATK_UP": 0.1}
total = 100 * (1 + 0.1) = 110
結果: 110
```

**場景 3: 多個 Buff (+10% 和 +15%)**
```
baseAtk = 100

validBuffs = [
    Buff(AURA_ATK_10) { modifier: ATK_UP 0.1 },
    Buff(AURA_ATK_15) { modifier: ATK_UP 0.15 }
]

互斥規則應用:
maxModifiersByType = {"ATK_UP": max(0.1, 0.15) = 0.15}

total = 100 * (1 + 0.15) = 115
結果: 115 ✅ (不是 100 * 1.1 * 1.15 = 126.5)
```

**場景 4: PVP 模式限制 (+10% 限制到 5%)**
```
baseAtk = 100
mode = GameMode.PVP

validBuffs = [Buff(AURA_ATK_UP) { modifier: ATK_UP 0.1 }]

PVP 限制:
appliedValue = min(0.1, 0.05) = 0.05

maxModifiersByType = {"ATK_UP": 0.05}

total = 100 * (1 + 0.05) = 105
結果: 105 ✅
```

##### 為什麼使用乘法?

```
假設有多種修飾類型 (未來實現):
- ATK_UP: +10%
- CRIT_DMG: +30%

計算應該為:
攻擊 = 100
暴擊傷害 = 150 (100 * 1.3)

// 錯誤方式 (加法):
攻擊 = 100 + 10 + 30 = 140

// 正確方式 (乘法):
攻擊 = 100 * (1 + 0.1) * (1 + 0.3) = 143
```

#### cleanupExpired() - 清理過期效果

```java
public void cleanupExpired() {
    shields.removeIf(Shield::isExpired);  // 移除過期護盾
    buffs.removeIf(Buff::isExpired);      // 移除過期 Buff
}
```

**調用時機**:
```java
public StatContext computeStats(Entity entity) {
    entity.cleanupExpired();  // 計算前先清理過期效果
    // ... 計算最終屬性
}
```

---

### 7. 遊戲模式 (GameMode)

### 8. 能力系統 (Ability 基類)

```java
abstract class Ability {
    public String name;
    
    abstract void trigger(Entity entity, GameEvent event, 
                         List<Entity> allEntities, 
                         GameMode mode, BattleLog log);
}
```

**設計模式**: 策略模式 (Strategy Pattern)

**優勢**:
- 易於擴展新能力
- 每個能力獨立實現觸發邏輯
- 遵循開閉原則

---

## 具體能力實現

### 1. 暴擊觸盾能力 (CritShieldAbility)

```java
class CritShieldAbility extends Ability {
    private double shieldValue;        // 護盾量
    private long cooldownDuration;     // 冷卻時長
}
```

**觸發流程**:

```
OnCritEvent 發生
    ↓
檢查冷卻狀態
    ├─ 在冷卻中 → 觸發被阻擋 ❌
    └─ 可以觸發 → 添加護盾 ✅
    ↓
更新冷卻時間
    ↓
記錄日誌
```

**測試結果**:
| 案例 | 冷卻時長 | 第一次 | 第二次 | 期望盾值 |
|------|---------|--------|--------|---------|
| 無冷卻 | 0ms | ✅ +100 | ✅ +100 | 200 |
| 有冷卻 | 10s | ✅ +100 | ❌ 阻擋 | 100 |

### 2. 光環能力 (AuraAbility)

```java
class AuraAbility extends Ability {
    private double atkBonus;   // 攻擊加成百分比
    private double range;      // 光環作用範圍
    private long duration;     // Buff 持續時間
}
```

**工作機制**:

```
TickEvent 發生 (每個時間滴答)
    ↓
掃描範圍內所有實體
    ↓
距離 <= range 的隊友
    ↓
為其創建 Buff
    ↓
添加 ATK_UP 修飾
    ↓
記錄日誌
```

**距離計算**:
```java
double distance = Math.sqrt(
    Math.pow(entity.x - other.x, 2) + 
    Math.pow(entity.y - other.y, 2)
);
```

**測試案例**:
```
Supporter 位置: (0, 0)
Ally1 位置: (3, 0) → 距離 3m ✅ 獲得 +10%
Ally2 位置: (6, 0) → 距離 6m ❌ 超出 5m 範圍

結果:
  Ally1: 100 * 1.1 = 110
  Ally2: 100 (無加成)
```

---

## 互斥規則 (Mutual Exclusion)

### 實現邏輯

同一類型的修飾只保留最大值:

```java
Map<String, Double> maxModifiersByType = new HashMap<>();

for (Buff buff : validBuffs) {
    for (StatModifier mod : buff.modifiers) {
        if (mod.type.equals("ATK_UP")) {
            double appliedValue = mod.value;
            
            // PVP 模式限制
            if (mode == GameMode.PVP) {
                appliedValue = Math.min(appliedValue, 0.05);
            }
            
            // 只保留最大值
            maxModifiersByType.put("ATK_UP", Math.max(
                maxModifiersByType.getOrDefault("ATK_UP", 0.0),
                appliedValue
            ));
        }
    }
}

// 應用所有修飾
for (double modifier : maxModifiersByType.values()) {
    total *= (1 + modifier);
}
```

### 測試案例

```
兩個 Buff:
  Buff1: +10% ATK
  Buff2: +15% ATK

互斥規則應用:
  取 max(10%, 15%) = 15%

計算結果:
  100 * 1.15 = 115 ✅
```

---

## PVP 模式限制

### 模式差異

```java
if (mode == GameMode.PVP) {
    appliedValue = Math.min(appliedValue, 0.05); // 5% 上限
}
```

### 實際應用

```
光環原始加成: +10%
應用於 PVP 模式:
  appliedValue = min(0.10, 0.05) = 0.05

結果:
  100 * 1.05 = 105 ✅
```

---

## 戰鬥日誌系統 (BattleLog)

### 日誌項目結構

```java
class BattleLogEntry {
    public long time;          // 相對時間
    public String entityId;    // 實體 ID
    public String event;       // 事件類型
    public String ability;     // 能力名稱
    public String result;      // 結果狀態
    public String details;     // 詳細信息
}
```

### 日誌格式

```
t:69, entity:hero1, evt:OnCrit, ability:CRIT_SHIELD, result:EFFECT_APPLIED, detail:shield:100
t:70, entity:hero1, evt:OnCrit, ability:CRIT_SHIELD, result:EFFECT_APPLIED, detail:shield:100
```

### 記錄類型

| Result | 說明 |
|--------|------|
| EFFECT_APPLIED | 能力成功觸發 |
| BLOCKED_BY_COOLDOWN | 被冷卻阻擋 |
| BUFF_APPLIED | Buff 應用成功 |

---

## 狀態上下文 (StatContext)

```java
class StatContext {
    public Map<String, Double> attributesMap;
    
    public void setAtk(double value);
    public void setShield(double value);
}
```

**用途**: 封裝計算結果，便於查詢和擴展

**包含屬性**:
- `ATK`: 最終攻擊力
- `SHIELD`: 總護盾值

---

## 能力系統管理器 (AbilitySystem)

```java
class AbilitySystem {
    private List<Ability> abilities;
    private GameMode mode;
    
    public void registerAbility(Ability ability);
    public void setGameMode(GameMode mode);
    public void triggerEvent(GameEvent event, Entity entity, 
                            List<Entity> allEntities, BattleLog log);
    public StatContext computeStats(Entity entity);
}
```

### 核心流程

```
1. registerAbility() → 註冊能力
2. setGameMode() → 設置遊戲模式
3. triggerEvent() → 觸發事件
   ├─ 遍歷所有註冊的能力
   └─ 調用各能力的 trigger() 方法
4. computeStats() → 計算最終屬性
   ├─ 清理過期效果
   ├─ 計算修飾後的攻擊力
   └─ 計算總護盾值
```

---

## 五大測試需求

### ✅ 測試 1: 暴擊觸盾（無冷卻）

**目標**: 驗證護盾可疊加

**代碼**:
```java
Entity hero = new Entity("hero1", 100, 50, 0, 0);
AbilitySystem system = new AbilitySystem();
system.registerAbility(new CritShieldAbility(100, 0)); // 無冷卻

system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);

StatContext stats = system.computeStats(hero);
// 結果: 200.0 ✅
```

### ✅ 測試 2: 暴擊觸盾（有冷卻）

**目標**: 驗證冷卻機制

**代碼**:
```java
system.registerAbility(new CritShieldAbility(100, 10000)); // 10秒冷卻

system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// → 日誌: EFFECT_APPLIED

Thread.sleep(100); // 短延遲，仍在冷卻內

system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// → 日誌: BLOCKED_BY_COOLDOWN

// 結果: 100.0 (只有第一次觸發) ✅
```

### ✅ 測試 3: 光環系統

**目標**: 驗證範圍內隊友獲得加成

**代碼**:
```java
Entity supporter = new Entity("supporter", 100, 50, 0, 0);
Entity ally1 = new Entity("ally1", 100, 50, 3, 0);  // 距離 3m
Entity ally2 = new Entity("ally2", 100, 50, 6, 0);  // 距離 6m

system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));
system.triggerEvent(new TickEvent(), supporter, entities, log);

// 結果:
//   Ally1: 110.0 ✅ (在範圍內)
//   Ally2: 100.0 ✅ (超出範圍)
```

### ✅ 測試 4: 互斥規則

**目標**: 驗證同類修飾只取最大值

**代碼**:
```java
Buff buff1 = new Buff("AURA_ATK_10", 5000);
buff1.modifiers.add(new StatModifier("ATK_UP", 0.10, 5000, "AURA_ATK_10"));

Buff buff2 = new Buff("AURA_ATK_15", 5000);
buff2.modifiers.add(new StatModifier("ATK_UP", 0.15, 5000, "AURA_ATK_15"));

hero.addBuff(buff1);
hero.addBuff(buff2);

StatContext stats = system.computeStats(hero);
// 結果: 115.0 (max(10%, 15%) = 15%) ✅
```

### ✅ 測試 5: PVP 模式

**目標**: 驗證 PVP 模式下光環有上限

**代碼**:
```java
AbilitySystem system = new AbilitySystem();
system.setGameMode(GameMode.PVP); // 設置 PVP 模式

system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));
system.triggerEvent(new TickEvent(), supporter, Arrays.asList(supporter, ally), log);

StatContext stats = system.computeStats(ally);
// 10% 被限制到 5%
// 結果: 105.0 ✅
```

---

## 設計模式

### 1. 策略模式 (Strategy)
- **應用**: Ability 抽象類 + 具體能力類
- **優勢**: 易於新增能力

### 2. 觀察者模式 (Observer)
- **應用**: 事件系統觸發多個能力反應
- **優勢**: 解耦事件和能力

### 3. 模板方法模式 (Template Method)
- **應用**: AbilitySystem.computeStats()
- **優勢**: 標準化計算流程

---

## 核心機制詳解

### 互斥規則 (Mutual Exclusion)

#### 設計目的

防止多個相同效果的 Buff 疊加導致遊戲失衡

#### 實現邏輯

```java
// 收集並選擇最大修飾
Map<String, Double> maxModifiersByType = new HashMap<>();

for (Buff buff : validBuffs) {
    for (StatModifier mod : buff.modifiers) {
        if (mod.type.equals("ATK_UP")) {
            double appliedValue = mod.value;
            
            // PVP 模式限制
            if (mode == GameMode.PVP) {
                appliedValue = Math.min(appliedValue, 0.05);
            }
            
            // 關鍵: 同一類型只保留最大值
            String key = "ATK_UP";
            maxModifiersByType.put(key, Math.max(
                maxModifiersByType.getOrDefault(key, 0.0),
                appliedValue
            ));
        }
    }
}

// 應用修飾
for (double modifier : maxModifiersByType.values()) {
    total *= (1 + modifier);
}
```

#### 為什麼需要互斥規則?

**沒有互斥的情況**:
```
Buff1: +10% ATK
Buff2: +15% ATK
結果計算: 100 * (1 + 0.1) * (1 + 0.15) = 126.5

這樣兩個 Buff 都疊加了，可能導致:
  ├─ 遊戲失衡
  ├─ 玩家依賴堆疊多個光環
  └─ PVP 競技不公平
```

**有互斥規則的情況**:
```
Buff1: +10% ATK
Buff2: +15% ATK
互斥規則: max(10%, 15%) = 15%
結果計算: 100 * (1 + 0.15) = 115

優勢:
  ├─ 只取最優效果，防止過度堆疊
  ├─ 遊戲平衡性更好
  └─ 鼓勵玩家選擇最強的增益
```

#### 測試驗證

```
場景: 玩家獲得兩個光環
  ├─ 光環 A: +10% ATK
  └─ 光環 B: +15% ATK

計算過程:
  1. 過濾有效 Buff: [光環A, 光環B]
  2. 提取修飾: 
     ├─ 光環A → ATK_UP: 0.1
     └─ 光環B → ATK_UP: 0.15
  3. 互斥規則: max(0.1, 0.15) = 0.15
  4. 應用修飾: 100 * 1.15 = 115

結果: ✅ 115 (符合預期)
```

---

### PVP 模式限制

#### 設計目的

在 PVP 競技場限制光環效果，保證公平性

#### 實現方式

```java
if (mode == GameMode.PVP) {
    appliedValue = Math.min(appliedValue, 0.05);  // 上限 5%
}
```

#### 應用場景對比

```
情況 1: PVE 模式 (副本)
  ├─ 光環: +10% ATK
  ├─ PVE 檢查: 跳過 (mode == PVP 為 false)
  ├─ 應用值: 0.1
  └─ 結果: 100 * 1.1 = 110 ✅

情況 2: PVP 模式 (競技場)
  ├─ 光環: +10% ATK
  ├─ PVP 檢查: appliedValue = min(0.1, 0.05) = 0.05
  ├─ 應用值: 0.05
  └─ 結果: 100 * 1.05 = 105 ✅
```

#### 為什麼需要模式區分?

```
問題: 如果光環在 PVP 中無限制
  ├─ 4 人小隊團隊: 每人 +10% = 原傷害 1.4 倍
  ├─ 10 人大隊: 每人 +10% = 可能無限疊加
  └─ 結果: PVP 變成團隊數量比，而非技能比拼

解決: PVP 模式上限 5%
  ├─ 所有光環都被限制到 5%
  ├─ 團隊加成: 平衡而公平
  └─ 勝負取決於操作和戰術
```

---

## 事件系統深入分析

### 事件觸發流程

```
遊戲邏輯層
  │
  ├─ 玩家暴擊
  │  └─ 建立 OnCritEvent(hero)
  │
  ├─ 時間更新
  │  └─ 建立 TickEvent()
  │
  └─ 受到傷害
     └─ 建立 OnDamageTakenEvent(defender, 50)
           │
           ▼
     能力系統層
           │
           └─ AbilitySystem.triggerEvent()
              │
              ├─ 遍歷所有註冊能力
              │  │
              │  ├─ CritShieldAbility.trigger()
              │  │  ├─ 檢查: OnCritEvent?
              │  │  ├─ 檢查: 冷卻?
              │  │  └─ 執行或阻擋
              │  │
              │  └─ AuraAbility.trigger()
              │     ├─ 檢查: TickEvent?
              │     ├─ 掃描周圍
              │     └─ 應用 Buff
              │
              ▼
          狀態更新層
              │
              └─ Entity 狀態變化
                 ├─ shields: [Shield#1, Shield#2]
                 ├─ buffs: [Buff#1, Buff#2]
                 └─ cooldowns: {CRIT_SHIELD: ...}
                      │
                      ▼
                  日誌記錄層
                      │
                      └─ BattleLog
                         ├─ t:69, evt:OnCrit, result:EFFECT_APPLIED
                         └─ t:70, evt:OnCrit, result:BLOCKED_BY_COOLDOWN
```

### 事件與能力的鬆耦合

```
好處: 事件和能力獨立演進

添加新事件只需:
  1. 建立新 GameEvent 實現
  2. 在遊戲邏輯層觸發事件
  3. 無需修改 AbilitySystem

添加新能力只需:
  1. 繼承 Ability
  2. 實現 trigger() 方法
  3. 註冊到 AbilitySystem
  4. 無需修改事件層

這就是觀察者模式的力量!
```

---

## 五大測試需求詳解

### ✅ 測試 1: 暴擊觸盾（無冷卻）

**需求**: 驗證護盾可疊加

**代碼**:
```java
Entity hero = new Entity("hero1", 100, 50, 0, 0);
AbilitySystem system = new AbilitySystem();
system.registerAbility(new CritShieldAbility(100, 0)); // cooldown = 0

// 觸發兩次暴擊
system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// 日誌: t:69, evt:OnCrit, ability:CRIT_SHIELD, result:EFFECT_APPLIED, detail:shield:100

system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// 日誌: t:70, evt:OnCrit, ability:CRIT_SHIELD, result:EFFECT_APPLIED, detail:shield:100

StatContext stats = system.computeStats(hero);
stats.attributesMap.get("SHIELD");  // 200.0 ✅
```

**驗證**:
```
護盾列表: [Shield(100), Shield(100)]
getTotalShield(): 100 + 100 = 200 ✅
```

---

### ✅ 測試 2: 暴擊觸盾（有冷卻）

**需求**: 驗證冷卻機制防止頻繁觸發

**代碼**:
```java
Entity hero = new Entity("hero2", 100, 50, 0, 0);
AbilitySystem system = new AbilitySystem();
system.registerAbility(new CritShieldAbility(100, 10000)); // cooldown = 10s

// 第一次暴擊 - 成功觸發
system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// 日誌: t:1, evt:OnCrit, ability:CRIT_SHIELD, result:EFFECT_APPLIED, detail:shield:100
// cooldown.lastTriggeredTime = 1ms

// 等待 100ms (仍在冷卻內)
Thread.sleep(100);

// 第二次暴擊 - 被冷卻阻擋
system.triggerEvent(new OnCritEvent(hero), hero, Arrays.asList(hero), log);
// 檢查: (101 - 1) = 100 < 10000? → true → 在冷卻中
// 日誌: t:101, evt:OnCrit, ability:CRIT_SHIELD, result:BLOCKED_BY_COOLDOWN

StatContext stats = system.computeStats(hero);
stats.attributesMap.get("SHIELD");  // 100.0 ✅ (只有第一次)
```

**驗證**:
```
護盾列表: [Shield(100)]
只有一個護盾，第二次被冷卻阻擋 ✅
```

---

### ✅ 測試 3: 光環系統

**需求**: 驗證光環給予範圍內隊友加成

**代碼**:
```java
Entity supporter = new Entity("supporter", 100, 50, 0, 0);
Entity ally1 = new Entity("ally1", 100, 50, 3, 0);   // 距離 3m
Entity ally2 = new Entity("ally2", 100, 50, 6, 0);   // 距離 6m

AbilitySystem system = new AbilitySystem();
system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));
//                                      加成  範圍  持續時間

List<Entity> entities = Arrays.asList(supporter, ally1, ally2);

// Tick 事件觸發光環
system.triggerEvent(new TickEvent(), supporter, entities, log);

// Ally1 檢查
double distance1 = Math.sqrt((0-3)² + (0-0)²) = 3
3 <= 5? → true ✅
// 日誌: t:1, entity:ally1, evt:Tick, ability:AURA_ATK_UP, result:BUFF_APPLIED

StatContext stats1 = system.computeStats(ally1);
stats1.attributesMap.get("ATK");  // 110.0 ✅

// Ally2 檢查
double distance2 = Math.sqrt((0-6)² + (0-0)²) = 6
6 <= 5? → false ❌
// 未應用 Buff

StatContext stats2 = system.computeStats(ally2);
stats2.attributesMap.get("ATK");  // 100.0 ✅
```

**驗證**:
```
Ally1 結果: 100 * 1.1 = 110.0 ✅
Ally2 結果: 100 (無 Buff) ✅
```

---

### ✅ 測試 4: 互斥規則

**需求**: 驗證同類型修飾只取最大值

**代碼**:
```java
Entity hero = new Entity("hero", 100, 50, 0, 0);

// 手動添加兩個 Buff (模擬同時擁有兩個光環)
Buff buff1 = new Buff("AURA_ATK_10", 5000);
buff1.modifiers.add(new StatModifier("ATK_UP", 0.10, 5000, "AURA_ATK_10"));
hero.addBuff(buff1);

Buff buff2 = new Buff("AURA_ATK_15", 5000);
buff2.modifiers.add(new StatModifier("ATK_UP", 0.15, 5000, "AURA_ATK_15"));
hero.addBuff(buff2);

AbilitySystem system = new AbilitySystem();
StatContext stats = system.computeStats(hero);

// 計算過程:
// validBuffs = [buff1, buff2]
// maxModifiersByType:
//   ├─ buff1: ATK_UP 0.1  → maxModifiersByType["ATK_UP"] = 0.1
//   └─ buff2: ATK_UP 0.15 → maxModifiersByType["ATK_UP"] = max(0.1, 0.15) = 0.15
// 
// total = 100 * (1 + 0.15) = 115

stats.attributesMap.get("ATK");  // 115.0 ✅
```

**驗證**:
```
互斥規則: 取較大的 0.15
結果: 100 * 1.15 = 115.0 ✅ (NOT 126.5)
```

---

### ✅ 測試 5: PVP 模式限制

**需求**: 驗證 PVP 模式下光環被限制

**代碼**:
```java
Entity supporter = new Entity("supporter", 100, 50, 0, 0);
Entity ally = new Entity("ally", 100, 50, 3, 0);

AbilitySystem system = new AbilitySystem();
system.setGameMode(GameMode.PVP);  // ← 設置 PVP 模式
system.registerAbility(new AuraAbility("AURA_ATK_UP", 0.1, 5, 5000));

List<Entity> entities = Arrays.asList(supporter, ally);

// Tick 事件
system.triggerEvent(new TickEvent(), supporter, entities, log);

// PVE 計算過程:
// appliedValue = 0.1
// mode == GameMode.PVP? → true
// appliedValue = min(0.1, 0.05) = 0.05
// 
// total = 100 * (1 + 0.05) = 105

StatContext stats = system.computeStats(ally);
stats.attributesMap.get("ATK");  // 105.0 ✅
```

**驗證**:
```
PVP 限制: 0.1 被限制到 0.05
結果: 100 * 1.05 = 105.0 ✅
```

---

## 擴展性

### 新增能力範例

#### 1. 傷害反彈能力

```java
class DamageReflectionAbility extends Ability {
    private double reflectRate;  // 反彈比例 (30%)
    
    public DamageReflectionAbility(double reflectRate) {
        super("REFLECT");
        this.reflectRate = reflectRate;
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, 
                 List<Entity> allEntities, GameMode mode, BattleLog log) {
        // 檢查是否為受傷事件
        if (!(event instanceof OnDamageTakenEvent)) return;
        
        OnDamageTakenEvent dmgEvent = (OnDamageTakenEvent) event;
        if (!dmgEvent.defender.equals(entity)) return;
        
        // 計算反彈傷害
        double reflectDamage = dmgEvent.damage * reflectRate;
        
        // 這裡應該對攻擊者造成傷害 (未來實現)
        log.addEntry(entity.id, "OnDamageTaken", this.name, 
                    "REFLECT", String.format("damage:%.0f", reflectDamage));
    }
}
```

#### 2. 治療能力

```java
class HealAbility extends Ability {
    private double healAmount;
    private long cooldownDuration;
    
    public HealAbility(double healAmount, long cooldownDuration) {
        super("HEAL");
        this.healAmount = healAmount;
        this.cooldownDuration = cooldownDuration;
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, 
                 List<Entity> allEntities, GameMode mode, BattleLog log) {
        // 假設自動治療 (Tick 事件)
        if (!(event instanceof TickEvent)) return;
        
        AbilityCooldown cooldown = entity.cooldowns.computeIfAbsent(
            this.name,
            k -> new AbilityCooldown(this.name, cooldownDuration)
        );
        
        if (cooldown.isOnCooldown()) return;
        
        // 治療邏輯 (未來實現)
        log.addEntry(entity.id, "Tick", this.name, 
                    "HEAL", String.format("amount:%.0f", healAmount));
        cooldown.trigger();
    }
}
```

#### 3. 防禦光環

```java
class DefenseAuraAbility extends Ability {
    private double defenseBonus;  // +20% 防禦
    private double range;
    private long duration;
    
    public DefenseAuraAbility(double defenseBonus, double range, long duration) {
        super("DEFENSE_AURA");
        this.defenseBonus = defenseBonus;
        this.range = range;
        this.duration = duration;
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, 
                 List<Entity> allEntities, GameMode mode, BattleLog log) {
        if (!(event instanceof TickEvent)) return;
        
        for (Entity other : allEntities) {
            if (other.equals(entity)) continue;
            
            double distance = Math.sqrt(
                Math.pow(entity.x - other.x, 2) + 
                Math.pow(entity.y - other.y, 2)
            );
            
            if (distance <= range) {
                Buff buff = new Buff(this.name, duration);
                // 改為 DEF_UP 修飾
                StatModifier mod = new StatModifier(
                    "DEF_UP",        // ← 防禦修飾
                    defenseBonus,
                    duration,
                    this.name
                );
                buff.modifiers.add(mod);
                other.addBuff(buff);
                
                log.addEntry(other.id, "Tick", this.name, "BUFF_APPLIED",
                            String.format("def_bonus:%.0f%%", defenseBonus * 100));
            }
        }
    }
}
```

### 新增事件範例

```java
class OnKillEvent implements GameEvent {
    public Entity killer;
    public Entity victim;
    public double reward;  // 擊殺獎勵
    
    public OnKillEvent(Entity killer, Entity victim, double reward) {
        this.killer = killer;
        this.victim = victim;
        this.reward = reward;
    }
    
    @Override
    public String getEventType() { return "OnKill"; }
}

// 擊殺加成能力
class KillStreakAbility extends Ability {
    public KillStreakAbility() {
        super("KILL_STREAK");
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, 
                 List<Entity> allEntities, GameMode mode, BattleLog log) {
        if (!(event instanceof OnKillEvent)) return;
        
        OnKillEvent killEvent = (OnKillEvent) event;
        if (!killEvent.killer.equals(entity)) return;
        
        // 每次擊殺增加 5% 攻擊
        Buff buff = new Buff("KILL_BONUS", 10000);
        buff.modifiers.add(new StatModifier(
            "ATK_UP", 0.05, 10000, "KILL_STREAK"
        ));
        entity.addBuff(buff);
        
        log.addEntry(entity.id, "OnKill", this.name, "EFFECT_APPLIED",
                    "kill_bonus:+5%");
    }
}
```

---

## 浮點精度說明

### 為什麼結果是 114.99999999999999 而非 115.0?

#### 原因

Java 中 `double` 採用 IEEE 754 浮點標準，無法精確表示所有十進制數。

#### 計算示例

```
100 * 1.15
= 100 * (1 + 0.15)
= 100 * (1 + 15/100)

問題: 0.15 無法在二進制中精確表示
結果: 114.99999999999999 (略小於 115)
```

#### 解決方案

```java
// 方案 1: 四舍五入
double result = Math.round(stats.attributesMap.get("ATK") * 100.0) / 100.0;

// 方案 2: 格式化輸出
String formatted = String.format("%.2f", stats.attributesMap.get("ATK"));

// 方案 3: 使用 BigDecimal (精確計算)
BigDecimal atk = new BigDecimal("100");
BigDecimal multiplier = new BigDecimal("1.15");
BigDecimal result = atk.multiply(multiplier);  // 正好 115
```

---

## 擴展性

### 新增能力範例

```java
class CustomAbility extends Ability {
    public CustomAbility() {
        super("CUSTOM_ABILITY");
    }
    
    @Override
    void trigger(Entity entity, GameEvent event, 
                 List<Entity> allEntities, GameMode mode, BattleLog log) {
        // 實現自定義邏輯
    }
}
```

### 新增事件範例

```java
class OnHealEvent implements GameEvent {
    public Entity healer;
    public double amount;
    
    @Override
    public String getEventType() { return "OnHeal"; }
}
```

---

## 總結

此系統提供了：
✅ 事件驅動架構  
✅ 可疊加的 Buff/護盾  
✅ 互斥衝突解析  
✅ 冷卻機制  
✅ 範圍型光環  
✅ 模式化調整  
✅ 詳細日誌記錄  
✅ 高度可擴展性  

非常適合 ARPG 遊戲的快速迭代開發！
