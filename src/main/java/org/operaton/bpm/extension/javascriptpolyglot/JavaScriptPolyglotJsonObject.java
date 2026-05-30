package org.operaton.bpm.extension.javascriptpolyglot;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

/**
 * Mutable GraalVM object proxy for JSON objects.
 *
 * <p>Scripts can use normal JavaScript dot and bracket notation; writes and
 * deletes trigger the supplied change listener for process variable persistence.</p>
 */
public class JavaScriptPolyglotJsonObject implements ProxyObject {

    private final Map<String, Object> values;
    private final JavaScriptPolyglotValueMapper valueMapper;
    private final Runnable changeListener;

    public JavaScriptPolyglotJsonObject(Map<String, Object> values, JavaScriptPolyglotValueMapper valueMapper, Runnable changeListener) {
        this.values = values;
        this.valueMapper = valueMapper;
        this.changeListener = changeListener;
    }

    Map<String, Object> toPlainJavaMap() {
        return values;
    }

    @Override
    public Object getMember(String key) {
        return valueMapper.toJavaScriptJsonValue(values.get(key), changeListener);
    }

    @Override
    public Object getMemberKeys() {
        return values.keySet().toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return values.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        values.put(key, valueMapper.toPlainJavaValue(value));
        changeListener.run();
    }

    @Override
    public boolean removeMember(String key) {
        if (!values.containsKey(key)) {
            return false;
        }

        values.remove(key);
        changeListener.run();

        return true;
    }
}
