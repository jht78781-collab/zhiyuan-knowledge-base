package cn.enterprise.kb.dify;

public class DifyFailure extends RuntimeException {
    private final String code;
    private final boolean missing;
    private final boolean transientFailure;

    DifyFailure(String code, String message, boolean missing, boolean transientFailure) {
        super(message);
        this.code = code;
        this.missing = missing;
        this.transientFailure = transientFailure;
    }

    static DifyFailure config(String message) { return new DifyFailure("DIFY_CONFIG", message, false, false); }
    static DifyFailure invalid() {
        return new DifyFailure("DIFY_RESPONSE", "Dify 返回的数据格式不符合约定，请检查应用版本和配置。", false, false);
    }
    static DifyFailure io() {
        return new DifyFailure("DIFY_NETWORK", "连接 Dify 失败或请求超时，请检查服务状态。", false, true);
    }
    public String code() { return code; }
    public boolean missing() { return missing; }
    public boolean transientFailure() { return transientFailure; }
}
