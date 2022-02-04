package me.nepnep.msa4legacy.patches;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mojang.launcher.updater.LowerCaseEnumTypeAdapterFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LowerCaseEnumTypeAdapter<T> extends TypeAdapter<T> {
    private final Map<String, Object> lowercaseToConstant = new HashMap<String, Object>();

    public LowerCaseEnumTypeAdapter(T[] constants) {
        for (T constant : constants) {
            lowercaseToConstant.put(LowerCaseEnumTypeAdapterFactory.toLowercase(constant), constant);
        }
    }

    @Override
    public void write(JsonWriter jsonWriter, T value) throws IOException {
        if (value == null) {
            jsonWriter.nullValue();
        } else {
            jsonWriter.value(LowerCaseEnumTypeAdapterFactory.toLowercase(value));
        }
    }

    @Override
    public T read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull();
            return null;
        }
        return (T) lowercaseToConstant.get(jsonReader.nextString());
    }
}
