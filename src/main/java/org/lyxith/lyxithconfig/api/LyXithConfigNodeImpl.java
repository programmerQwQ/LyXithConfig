package org.lyxith.lyxithconfig.api;

import com.google.gson.*;
import java.util.*;

public class LyXithConfigNodeImpl implements LyXithConfigNode {
    private final LyXithConfigNodeImpl parent;
    private final String name;
    private Object value;
    private final Map<String, LyXithConfigNodeImpl> children = new HashMap<>();

    // 添加类型标识，便于序列化/反序列化
    private enum NodeType {
        OBJECT,  // 有子节点
        VALUE,   // 单个值
        ARRAY    // 列表值
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    // 构造方法
    public LyXithConfigNodeImpl() {
        this.parent = null;
        this.name = "";
    }

    public LyXithConfigNodeImpl(LyXithConfigNodeImpl parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Override
    public String toString() {
        return GSON.toJson(toJsonElement());
    }

    public LyXithConfigNode fromString(String jsonString) {
        JsonElement jsonElement = GSON.fromJson(jsonString, JsonElement.class);
        return fromJsonElement(jsonElement, null, "");
    }

    // 改进的序列化方法
    private JsonElement toJsonElement() {
        if (hasValue()) {
            // 值节点：直接序列化值
            if (value instanceof List<?> list) {
                // 列表值：使用JsonArray
                JsonArray jsonArray = new JsonArray();
                for (Object item : list) {
                    jsonArray.add(valueToJsonElement(item));
                }
                return jsonArray;
            } else {
                // 单个值
                return valueToJsonElement(value);
            }
        } else {
            // 对象节点：使用JsonObject
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<String, LyXithConfigNodeImpl> entry : children.entrySet()) {
                jsonObject.add(entry.getKey(), entry.getValue().toJsonElement());
            }
            return jsonObject;
        }
    }

    // 辅助方法：将值转换为JsonElement
    private JsonElement valueToJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        } else if (value instanceof String) {
            return new JsonPrimitive((String) value);
        } else if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        } else if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        } else {
            // 复杂对象使用GSON转换
            return GSON.toJsonTree(value);
        }
    }

    // 改进的反序列化方法
    private static LyXithConfigNodeImpl fromJsonElement(JsonElement jsonElement, LyXithConfigNodeImpl parent, String name) {
        LyXithConfigNodeImpl node = new LyXithConfigNodeImpl(parent, name);

        if (jsonElement == null || jsonElement.isJsonNull()) {
            return node;
        }

        if (jsonElement.isJsonObject()) {
            // JSON对象 -> 容器节点
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                LyXithConfigNodeImpl childNode = fromJsonElement(entry.getValue(), node, entry.getKey());
                node.children.put(entry.getKey(), childNode);
            }
        } else if (jsonElement.isJsonArray()) {
            // JSON数组 -> 列表值节点
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            List<Object> list = new ArrayList<>();
            for (JsonElement element : jsonArray) {
                list.add(jsonElementToValue(element));
            }
            node.setValue(list);
        } else if (jsonElement.isJsonPrimitive()) {
            // JSON基本类型 -> 单个值节点
            node.setValue(jsonElementToValue(jsonElement));
        }

        return node;
    }

    // 辅助方法：将JsonElement转换为Java值
    private static Object jsonElementToValue(JsonElement jsonElement) {
        if (jsonElement.isJsonNull()) {
            return null;
        }

        JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsString();
        } else if (primitive.isNumber()) {
            String numberStr = primitive.getAsString();
            // 根据格式判断是整数还是浮点数
            if (numberStr.contains(".") || numberStr.contains("e") || numberStr.contains("E")) {
                return primitive.getAsDouble();
            } else {
                try {
                    return primitive.getAsInt();
                } catch (NumberFormatException e) {
                    return primitive.getAsLong();
                }
            }
        } else if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }

        return jsonElement.toString();
    }

    // 其他方法保持不变，但逻辑更清晰
    @Override
    public boolean hasValue() {
        return value != null;
    }

    @Override
    public String getPath() {
        Deque<String> pathStack = new ArrayDeque<>();
        LyXithConfigNodeImpl currentNode = this;

        while (currentNode != null) {
            if (!currentNode.name.isEmpty()) {
                pathStack.push(currentNode.name);
            }
            currentNode = currentNode.parent;
        }

        return String.join(".", pathStack);
    }

    @Override
    public void addNode(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] pathParts = path.split("\\.");
        LyXithConfigNodeImpl currentNode = this;

        for (String part : pathParts) {
            currentNode.children.putIfAbsent(part, new LyXithConfigNodeImpl(currentNode, part));
            currentNode = currentNode.children.get(part);
        }
    }

    @Override
    public void delNode(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] pathParts = path.split("\\.");
        LyXithConfigNodeImpl currentNode = this;

        for (int i = 0; i < pathParts.length - 1; i++) {
            currentNode = currentNode.children.get(pathParts[i]);
            if (currentNode == null) {
                return;
            }
        }

        String targetNodeName = pathParts[pathParts.length - 1];
        currentNode.children.remove(targetNodeName);
    }

    @Override
    public void setValue(Object value) {
        this.value = value;
        // 设置值时清空子节点，确保值节点和容器节点互斥
        this.children.clear();
    }

    @Override
    public <T> Optional<T> getValue(Class<T> type) {
        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof List<?> list && !list.isEmpty()) {
            Object firstElement = list.getFirst();
            if (type.isInstance(firstElement)) {
                return Optional.of(type.cast(firstElement));
            }
        }

        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }

        return Optional.empty();
    }

    @Override
    public Optional<LyXithConfigNodeImpl> getNode(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.of(this);
        }

        String[] pathParts = path.split("\\.");
        LyXithConfigNodeImpl currentNode = this;

        for (String part : pathParts) {
            currentNode = currentNode.children.get(part);
            if (currentNode == null) {
                return Optional.empty();
            }
        }
        return Optional.of(currentNode);
    }

    // Getter方法
    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public Map<String, LyXithConfigNodeImpl> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    @Override
    public LyXithConfigNodeImpl getRoot() {
        LyXithConfigNodeImpl currentNode = this;
        while (currentNode.parent != null) {
            currentNode = currentNode.parent;
        }
        return currentNode;
    }

    @Override
    public void addNode(String path, Boolean overwrite) {
        if (overwrite || getNode(path).isEmpty()) {
            addNode(path);
        }
    }

    @Override
    public void initNode(String path, Boolean Overwrite, Object object) {
        addNode(path, Overwrite);
        getNode(path).ifPresent(node -> node.setValue(object));
    }

    // 列表操作
    @Override
    public int length() {
        if (hasValue()) {
            if (value instanceof List<?> list) {
                return list.size();
            } else {
                return 1;
            }
        } else {
            return children.size();
        }
    }

    @Override
    public void addElement(Object element) {
        if (hasValue()) {
            if (value instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                list.add(element);
            } else {
                List<Object> newList = new ArrayList<>();
                newList.add(value);
                newList.add(element);
                setValue(newList);
            }
        } else {
            setValue(element);
        }
    }

    @Override
    public void delElement(int index) {
        if (hasValue() && value instanceof List<?> list) {
            list.remove(index);
        }
    }

    @Override
    public void setElement(Object element, int index) {
        if (hasValue() && value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            list.set(index, element);
        }
    }

    @Override
    public Object getElement(int index) {
        if (hasValue() && value instanceof List<?> list) {
            return list.get(index);
        }
        return null;
    }
}