package xpizzax.emoticons.client;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Optional CIT Resewn support; no linked dependency or configuration changes. */
public final class CitAttachmentModels {
    private static final Object CONFIG;
    private static final Field ENABLED;
    static {
        Object config = null; Field enabled = null;
        try {
            Class<?> type = Class.forName("shcm.shsupercm.fabric.citresewn.config.CITResewnConfig");
            config = type.getField("INSTANCE").get(null);
            enabled = type.getField("enabled");
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        CONFIG = config; ENABLED = enabled;
    }
    public static boolean enabled() {
        try { return ENABLED != null && ENABLED.getBoolean(CONFIG); }
        catch (IllegalAccessException ignored) { return false; }
    }
    private record Rule(Set<Identifier> items, int[] ranges, String hand,
            Identifier model, int weight, String path) {
        boolean matches(Identifier item, int cmd, boolean offhand) {
            if (!items.contains(item) || (hand.equals("off") && !offhand)
                    || (hand.equals("main") && offhand)) return false;
            for (int i = 0; i < ranges.length; i += 2)
                if (cmd >= ranges[i] && cmd <= ranges[i+1]) return true;
            return false;
        }
    }
    private static final List<Rule> RULES = new ArrayList<>();
    private static boolean loaded;
    private CitAttachmentModels() {}
    public static void reload() { RULES.clear(); loaded = false; }

    public static Identifier select(ResourceManager manager, ItemStack stack, boolean offhand) {
        if (!enabled() || !stack.hasNbt() || !stack.getNbt().contains("CustomModelData", 99)) return null;
        if (!loaded) load(manager);
        Identifier item = Registries.ITEM.getId(stack.getItem());
        int cmd = stack.getNbt().getInt("CustomModelData");
        for (Rule rule : RULES) if (rule.matches(item, cmd, offhand)) return rule.model;
        return null;
    }
    private static void load(ResourceManager manager) {
        loaded = true;
        // Rules with other dynamic conditions stay entirely with the CIT engine.
        Set<String> supported = Set.of("type", "items", "matchItems", "model", "nbt.CustomModelData", "hand", "weight");
        for (String directory : List.of("optifine/cit", "citresewn/cit")) {
            var files = manager.findResources(directory, id -> id.getPath().endsWith(".properties"));
            for (var entry : files.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                    Properties properties = new Properties();properties.load(reader);
                    if (!properties.getProperty("type", "item").equals("item")
                            || !supported.containsAll(properties.stringPropertyNames())) continue;
                    String itemNames = properties.getProperty("items", properties.getProperty("matchItems", ""));
                    String target = properties.getProperty("model", "");
                    String predicate = properties.getProperty("nbt.CustomModelData", "");
                    String hand = properties.getProperty("hand", "any");
                    if (itemNames.isBlank() || target.isBlank() || predicate.isBlank()
                            || !Set.of("any", "off", "main").contains(hand)) continue;
                    var items = new java.util.HashSet<Identifier>();
                    for (String name : itemNames.trim().split("\\s+"))
                        items.add(name.contains(":") ? new Identifier(name) : new Identifier("minecraft", name));
                    var ranges = new ArrayList<Integer>();
                    for (String range : predicate.trim().split("\\s+")) {
                        if (!range.matches("[0-9]+(?:-[0-9]+)?")) { ranges.clear();break; }
                        String[] ends = range.split("-");int low = Integer.parseInt(ends[0]);
                        ranges.add(low);ranges.add(ends.length == 2 ? Integer.parseInt(ends[1]) : low);
                    }
                    if (ranges.isEmpty()) continue;
                    int weight = Integer.parseInt(properties.getProperty("weight", "0"));
                    RULES.add(new Rule(Set.copyOf(items), ranges.stream().mapToInt(Integer::intValue).toArray(),
                            hand, modelPath(entry.getKey(), target), weight, entry.getKey().toString()));
                } catch (Exception ignored) {}
            }
        }
        RULES.sort(Comparator.comparingInt(Rule::weight).reversed().thenComparing(Rule::path));
    }
    private static Identifier modelPath(Identifier property, String target) {
        if (target.endsWith(".json")) target = target.substring(0, target.length()-5);
        if (target.contains(":")) return new Identifier(target);
        if (target.startsWith("item/") || target.startsWith("block/")) return new Identifier("minecraft", target);
        String folder = property.getPath().substring(0, property.getPath().lastIndexOf('/')+1);
        var parts = new java.util.ArrayDeque<String>();
        for (String part : (folder + target).split("/")) {
            if (part.equals("..")) { if (parts.isEmpty()) throw new IllegalArgumentException("invalid CIT path");parts.removeLast(); }
            else if (!part.isEmpty() && !part.equals(".")) parts.addLast(part);
        }
        return new Identifier(property.getNamespace(), String.join("/", parts));
    }
}
