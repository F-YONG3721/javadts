class StatModifier // 屬性修飾器
{
    public String type; // "ATK_UP", "DEF_UP" 等
    public double value; // 百分比或絕對值
    public long expirationTime; // 毫秒
    public String source; // 來源能力名稱
    
    public StatModifier(String type, double value, long duration, String source) {
        this.type = type;
        this.value = value;
        this.expirationTime = System.currentTimeMillis() + duration;
        this.source = source;
    }
}
