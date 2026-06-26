package cc.wechat.observatory.gateway;

public final class WebSocketFrame {
    public final int opcode;
    public final byte[] payload;

    public WebSocketFrame(int opcode, byte[] payload) {
        this.opcode = opcode;
        this.payload = payload == null ? new byte[0] : payload;
    }
}
