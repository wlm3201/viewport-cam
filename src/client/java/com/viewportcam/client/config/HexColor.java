package com.viewportcam.client.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public final class HexColor extends TypeAdapter<Integer> {

    public static final int WHITE = 0xFFFFFFFF;

    @Override
    public void write(JsonWriter out, Integer value) throws IOException {
        out.value("#" + String.format("%08X", value == null ? WHITE : value));
    }

    @Override
    public Integer read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return WHITE;
        }
        if (in.peek() == JsonToken.NUMBER) {
            return in.nextInt();
        }
        String text = in.nextString().trim();
        try {
            return (int) Long.parseLong(text.startsWith("#") ? text.substring(1) : text, 16);
        } catch (NumberFormatException e) {
            return WHITE;
        }
    }
}
