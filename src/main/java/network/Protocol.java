package network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Protocol {
    private final HashMap<String,String> map;

    public Protocol() {
        this.map = new HashMap<>();
    }

    public Protocol(String s) {
        this.map = new HashMap<>();
        String[] pairs = s.split("\"\\$,\\$\"");
        for (String pair : pairs) {
            String[] keyValue = pair.split("\"\\$:\\$\"");
            if (keyValue.length == 2) {
                String key = unescape(keyValue[0]);
                String value = unescape(keyValue[1]);
                this.map.put(key, value);
            } else {
                throw new IllegalArgumentException("Illegal protocol: " + pair);
            }
        }
    }

    public void put(String key, String value) {
        String escapedKey = escape(key);
        String escapedValue = escape(value);
        this.map.put(escapedKey, escapedValue);
    }

    public String getString(String key) {
        String value = this.get(key);
        return Objects.requireNonNullElse(value, "");
    }

    private String get(String key) {
        return this.map.get(key);
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        } else {
            return input.replace("\"$:$\"", "\"\\$:\\$\"")
                        .replace("\"$,$\"", "\"\\$,\\$\"");
        }
    }

    private String unescape(String input) {
        if (input == null) {
            return "";
        } else {
            return input.replace("\"\\$:\\$\"", "\"$:$\"")
                        .replace("\"\\$,\\$\"", "\"$,$\"");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (count > 0) {
                String entrySeparator = "\"$,$\"";
                sb.append(entrySeparator);
            }
            String pairSeparator = "\"$:$\"";
            sb.append(entry.getKey()).append(pairSeparator).append(entry.getValue());
            count++;
        }
        return sb.toString();
    }
}
