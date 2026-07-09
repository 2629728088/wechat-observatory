package cc.wechat.observatory.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WechatImageInfoSnapshot {
    private final long localInfoId;
    private final List<String> values;
    private final List<String> fieldDebug;

    private WechatImageInfoSnapshot(long localInfoId, List<String> values, List<String> fieldDebug) {
        this.localInfoId = localInfoId;
        this.values = immutableCopy(values);
        this.fieldDebug = immutableCopy(fieldDebug);
    }

    static WechatImageInfoSnapshot from(Object imageInfo) {
        return new WechatImageInfoSnapshot(
                WechatImageInfo.localId(imageInfo),
                WechatImageInfo.stringValues(imageInfo),
                WechatImageInfo.stringFieldDebug(imageInfo, 12, 96));
    }

    static WechatImageInfoSnapshot of(long localInfoId, List<String> values, List<String> fieldDebug) {
        return new WechatImageInfoSnapshot(localInfoId, values, fieldDebug);
    }

    long localInfoId() {
        return localInfoId;
    }

    List<String> values() {
        return values;
    }

    List<String> fieldDebug() {
        return fieldDebug;
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
