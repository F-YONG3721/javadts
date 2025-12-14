class Shield // 護盾
{
    public String abilityName;
    public double value;
    public long expirationTime;
    
    public Shield(String abilityName, double value, long duration) {
        this.abilityName = abilityName;
        this.value = value;
        this.expirationTime = System.currentTimeMillis() + duration;
    }
    
    public boolean isExpired() {
        // 護盾永不過期（使用 Long.MAX_VALUE）
        return false;
    }
}
