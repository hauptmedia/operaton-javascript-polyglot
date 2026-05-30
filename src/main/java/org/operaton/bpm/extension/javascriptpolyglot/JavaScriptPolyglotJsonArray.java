package org.operaton.bpm.extension.javascriptpolyglot;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.List;

/**
 * Mutable GraalVM array proxy for JSON arrays.
 *
 * <p>Mutations call the supplied change listener, which lets process variable
 * backed arrays immediately write their updated JSON value back into Operaton.</p>
 */
public class JavaScriptPolyglotJsonArray implements ProxyArray {

    private final List<Object> values;
    private final JavaScriptPolyglotValueMapper valueMapper;
    private final Runnable changeListener;

    public JavaScriptPolyglotJsonArray(List<Object> values, JavaScriptPolyglotValueMapper valueMapper, Runnable changeListener) {
        this.values = values;
        this.valueMapper = valueMapper;
        this.changeListener = changeListener;
    }

    List<Object> toPlainJavaList() {
        return values;
    }

    @Override
    public Object get(long index) {
        return valueMapper.toJavaScriptJsonValue(values.get(Math.toIntExact(index)), changeListener);
    }

    @Override
    public void set(long index, Value value) {
        var listIndex = Math.toIntExact(index);

        while (values.size() <= listIndex) {
            values.add(null);
        }

        values.set(listIndex, valueMapper.toPlainJavaValue(value));
        changeListener.run();
    }

    @Override
    public boolean remove(long index) {
        values.remove(Math.toIntExact(index));
        changeListener.run();

        return true;
    }

    @Override
    public long getSize() {
        return values.size();
    }
}
