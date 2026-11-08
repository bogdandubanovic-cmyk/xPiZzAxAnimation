package xpizzax.emoticons.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Classify the selected pack model, rather than the vanilla item or offset alone. */
public final class RpmAccessoryHelper {
    private enum Attachment { NONE, HAND, UMBRELLA, KNIFE, BACK_ITEM, BODY }
    private static <K, V> Map<K, V> boundedCache(int maximum) {
        return new LinkedHashMap<K, V>(64, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
                return size() > maximum;
            }
        };
    }
    private static final Map<String, Attachment> ATTACHMENT_CACHE = boundedCache(8192);
    private static final Map<Identifier, ModelInfo> MODEL_CACHE = boundedCache(2048);
    // Dynamix stores item/CMD -> model + actual texture mappings in several
    // override files. Load every item override, not only horse_items.
    private record DynamixRule(Identifier model, Attachment hint) {}
    private static final Map<String, DynamixRule> DYNAMIX_MODELS = new HashMap<>();
    private static final Set<Identifier> DYNAMIX_MODEL_IDS = new HashSet<>();
    private static boolean dynamixLoaded;
    private static ResourceManager lastManager;
    private static boolean lastCitEnabled;
    private static final Map<Identifier, Set<Integer>> LEGACY_FIXED = new HashMap<>();
    private static boolean legacyLoaded;
    private record Profile(String texture, Attachment attachment, JsonObject left) {}
    private static final Map<Identifier, Profile> PROFILES = new HashMap<>();
    private static boolean profilesLoaded;
    private record OverrideModel(float threshold, Identifier model) {}
    private record ModelInfo(Identifier parent, List<OverrideModel> overrides,
            Attachment hint, boolean elements, boolean leftDisplay, boolean sameDisplay,
            float x, float y, float z, boolean enlargedLeft, boolean handContact) {}
    private static final ModelInfo EMPTY = new ModelInfo(null, List.of(), Attachment.NONE,
            false, false, false, 0, 0, 0, false, false);
    private RpmAccessoryHelper() {}

    /** Also called for F3+T: ResourceManager identity can stay the same on reload. */
    public static void reload(ResourceManager manager) {
        lastManager = manager;
        ATTACHMENT_CACHE.clear();
        MODEL_CACHE.clear();
        DYNAMIX_MODELS.clear();
        DYNAMIX_MODEL_IDS.clear();
        dynamixLoaded = false;
        LEGACY_FIXED.clear();
        legacyLoaded = false;
        PROFILES.clear();
        profilesLoaded = false;
        CitAttachmentModels.reload();
        lastCitEnabled = CitAttachmentModels.enabled();
    }

    /** Torso attachment in vanilla feature coordinates.
     * Keep the grip position and use the vanilla left-hand -90-degree basis.
     * Match RPMClient 7.1.0's stationary offhand basis, including its small roll.
     * The model's display was authored against that basis, not a bare -90 X.
     * ItemRenderer still applies the pack's THIRD_PERSON_LEFT_HAND display transform. */
    public static void applyBodyAccessoryTransform(MatrixStack matrices)
    {
        matrices.translate(0.40F, 0.62F, -0.367308F);
        // RPM's original standing offhand: X=-0.42 rad, Z=-0.05 rad,
        // followed by vanilla's X=-90 degrees / Y=180 degrees.
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-0.42F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-0.05F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
    }

    public static void applyPackedToolBodyTransform(MatrixStack matrices) {
        applyBodyAccessoryTransform(matrices);
    }

    /** RPM RP 1.43.3: CMD 824 is authored as a waist-mounted 3D belt,
     * not as a hand-held model. Render without the left-hand display transform
     * and center its raw X/Z waist geometry on the lower torso. */
    public static boolean isToolBelt(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.GOLDEN_HORSE_ARMOR)
                && stack.hasNbt() && stack.getNbt().contains("CustomModelData", 99)
                && stack.getNbt().getInt("CustomModelData") == 824;
    }

    public static void applyToolBeltTransform(MatrixStack matrices) {
        // Baked item vertices are centered around the 8/16 model origin. CMD 824's
        // raw bounds are offset (-0.025, 0.22558, -0.15091). Center the waist mesh
        // around the player's torso and lower it to the belt line; retain RP scale.
        matrices.translate(0.01725F, 0.36435F, 0.10413F);
        matrices.scale(0.69F, 0.69F, 0.69F);
    }

    public static boolean isBodyAccessory(ItemStack stack) { return attachment(stack) == Attachment.BODY; }
    public static boolean isBodyAccessory(ItemStack stack, boolean offhand) { return attachment(stack, offhand) == Attachment.BODY; }
    public static boolean isKnifeItem(ItemStack stack) { return attachment(stack) == Attachment.KNIFE; }
    public static boolean isUmbrellaItem(ItemStack stack) { return attachment(stack) == Attachment.UMBRELLA; }
    public static boolean isAnimatedHandItem(ItemStack stack) {
        Attachment a = attachment(stack);
        return a == Attachment.HAND || a == Attachment.KNIFE || a == Attachment.UMBRELLA;
    }

    /** Fix RPM's frozen hand path in vanilla/OFF, only for actual noAnimation items. */
    public static boolean needsVanillaHandBinding(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()
                || !stack.getNbt().contains("CustomModelData", 99) || isBodyAccessory(stack)) return false;
        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
        if (manager != lastManager) reload(manager);
        if (!legacyLoaded) {
            legacyLoaded = true;
            try {
                Optional<Resource> resource = manager.getResource(new Identifier("minecraft", "rpmfiles/lefthand_render.json"));
                if (resource.isPresent()) try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("noAnimation")) for (JsonElement e : json.getAsJsonArray("noAnimation")) {
                        JsonObject rule = e.getAsJsonObject();
                        if (!rule.has("material") || !rule.has("customModelData")) continue;
                        Identifier id = modelId(rule.get("material").getAsString().toLowerCase(java.util.Locale.ROOT), "minecraft");
                        Set<Integer> values = LEGACY_FIXED.computeIfAbsent(id, key -> new HashSet<>());
                        for (JsonElement cmd : rule.getAsJsonArray("customModelData")) values.add(cmd.getAsInt());
                    }
                }
            } catch (Exception ignored) {}
        }
        Set<Integer> values = LEGACY_FIXED.get(Registries.ITEM.getId(stack.getItem()));
        return values != null && values.contains(stack.getNbt().getInt("CustomModelData"));
    }

    private static Attachment attachment(ItemStack stack) {
        return attachment(stack, true);
    }
    private static Attachment attachment(ItemStack stack, boolean offhand) {
        if (stack == null || stack.isEmpty()) return Attachment.NONE;
        boolean custom = stack.hasNbt() && stack.getNbt().contains("CustomModelData", 99);
        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
        if (manager != lastManager || lastCitEnabled != CitAttachmentModels.enabled()) reload(manager);
        int cmd = custom ? stack.getNbt().getInt("CustomModelData") : 0;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String key = itemId + ":" + cmd + ":" + offhand;
        Attachment cached = ATTACHMENT_CACHE.get(key);
        if (cached != null) return cached;
        Identifier model = CitAttachmentModels.select(manager, stack, offhand);
        boolean citModel = model != null;
        DynamixRule dynamixRule = null;
        if (model == null && custom) {
            dynamixRule = dynamixModel(manager, itemId, cmd);
            if (dynamixRule != null) model = dynamixRule.model();
        }
        boolean dynamixModel = dynamixRule != null;
        if (model == null) model = new Identifier(itemId.getNamespace(), "item/" + itemId.getPath());
        boolean bodyFamily = stack.isOf(Items.GOLDEN_HORSE_ARMOR) || stack.isOf(Items.IRON_HORSE_ARMOR)
                || stack.isOf(Items.CROSSBOW) || citModel || dynamixModel;
        Attachment inheritedHint = dynamixRule == null ? Attachment.NONE : dynamixRule.hint();
        Attachment result = resolve(manager, model, cmd, 0, bodyFamily, false, inheritedHint);
        ATTACHMENT_CACHE.put(key, result);
        return result;
    }

    /** Read every Dynamix item override so the classifier sees the real model
     * and texture even when the model JSON only declares dynamix:item/layer0. */
    private static DynamixRule dynamixModel(ResourceManager manager, Identifier item, int cmd) {
        if (!dynamixLoaded) loadDynamixModels(manager);
        return DYNAMIX_MODELS.get(item + ":" + cmd);
    }

    private static void loadDynamixModels(ResourceManager manager) {
        dynamixLoaded = true;
        try {
            Map<Identifier, Resource> files = manager.findResources("dynamix",
                    id -> id.getPath().endsWith(".overrides.json"));
            for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(
                        file.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!root.has("overrides") || !root.get("overrides").isJsonArray()) continue;
                    for (JsonElement element : root.getAsJsonArray("overrides")) {
                        if (!element.isJsonObject()) continue;
                        JsonObject rule = element.getAsJsonObject();
                        if (!rule.has("type") || !rule.get("type").getAsString().equalsIgnoreCase("ITEM")
                                || !rule.has("items") || !rule.has("customModelData") || !rule.has("models")) continue;
                        JsonObject models = rule.getAsJsonObject("models");
                        if (!models.has("parent")) continue;
                        Identifier selected = modelId(models.get("parent").getAsString(), "minecraft");
                        JsonObject textureJson = new JsonObject();
                        JsonObject textureValues = new JsonObject();
                        if (rule.has("textures") && rule.get("textures").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> texture : rule.getAsJsonObject("textures").entrySet()) {
                                JsonElement value = texture.getValue();
                                if (value.isJsonObject() && value.getAsJsonObject().has("location"))
                                    textureValues.add(texture.getKey(), value.getAsJsonObject().get("location"));
                                else if (value.isJsonPrimitive())
                                    textureValues.add(texture.getKey(), value);
                            }
                        }
                        textureJson.add("textures", textureValues);
                        Attachment hint = textureHint(textureJson, selected);
                        int cmd = rule.get("customModelData").getAsInt();
                        for (JsonElement item : rule.getAsJsonArray("items")) {
                            Identifier itemId = modelId(item.getAsString(), "minecraft");
                            DYNAMIX_MODELS.put(itemId + ":" + cmd, new DynamixRule(selected, hint));
                        }
                        DYNAMIX_MODEL_IDS.add(selected);
                    }
                } catch (Exception ignored) {
                    // Ignore one malformed optional override and continue with the rest.
                }
            }
        } catch (Exception ignored) {
            // A missing or malformed optional Dynamix table leaves vanilla/CIT handling intact.
        }
    }

    private static Attachment resolve(ResourceManager manager, Identifier model, int cmd,
            int depth, boolean bodyFamily, boolean backTool) {
        return resolve(manager, model, cmd, depth, bodyFamily, backTool, Attachment.NONE);
    }
    private static Attachment resolve(ResourceManager manager, Identifier model, int cmd,
            int depth, boolean bodyFamily, boolean backTool, Attachment inheritedHint) {
        if (depth > 16) return Attachment.NONE;
        ModelInfo info = modelInfo(manager, model);
        Identifier selected = null;
        // Minecraft uses the last matching override; CMD is a lower bound.
        for (OverrideModel override : info.overrides)
            if (cmd >= override.threshold) selected = override.model;
        if (selected != null && !selected.equals(model))
            return resolve(manager, selected, cmd, depth + 1, bodyFamily, backTool, inheritedHint);
        Attachment hint = inheritedHint == Attachment.NONE ? info.hint : inheritedHint;
        if (hint == Attachment.KNIFE || hint == Attachment.UMBRELLA
                || hint == Attachment.HAND) return hint;
        // A model explicitly named/textured as a cloak, cape, wing, tail,
        // backpack or other body-worn accessory is never a hand-held item.
        if (hint == Attachment.BODY) return Attachment.BODY;
        // Back-mounted weapons/tools often share a generic hand display, but
        // their actual mesh has no grip geometry at the left wrist. Keep true
        // handheld versions in HAND when the wrist-contact test succeeds.
        if (hint == Attachment.BACK_ITEM && info.elements && info.leftDisplay && !info.handContact)
            return Attachment.BODY;
        boolean displaced = info.leftDisplay && (Math.abs(info.x) >= 3.5F
                || info.y <= -4.5F || info.z >= 2.5F
                || (hint == Attachment.BODY && info.enlargedLeft));
        if (displaced && hint == Attachment.BODY) return Attachment.BODY;
        if (info.elements && info.leftDisplay && info.handContact) return Attachment.HAND;
        if (displaced && hint == Attachment.BACK_ITEM && (bodyFamily || !info.sameDisplay)) return Attachment.BODY;
        if (info.elements && info.leftDisplay) {
            // Authored model coordinates can compensate large display offsets.
            // This includes the pack's ordinary iron axe and musket: their
            // handles touch the wrist despite an unusual left display offset.
            if (info.handContact) return Attachment.HAND;
            if (info.sameDisplay && info.x > -3.5F) return Attachment.HAND;
            // A distinct left display which moves all geometry away from the
            // wrist belongs to the torso. Do not infer this from the item ID.
            if (displaced && !info.sameDisplay) return Attachment.BODY;
            return Attachment.HAND;
        }
        // An explicit, ordinary child grip overrides the parent's back display.
        if (info.leftDisplay && !displaced) return Attachment.HAND;
        if (info.parent != null)
            return resolve(manager, info.parent, cmd, depth + 1, bodyFamily, backTool, hint);
        return Attachment.NONE;
    }

    private static Identifier modelId(String value, String namespace) {
        return value.contains(":") ? new Identifier(value) : new Identifier(namespace, value);
    }
    private static void loadProfiles(ResourceManager manager) {
        if (profilesLoaded) return;
        profilesLoaded = true;
        try {
            Optional<Resource> resource = manager.getResource(new Identifier("xpizzax_emoticons", "attachment_profiles.json"));
            if (resource.isEmpty()) return;
            try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("profiles")) for (JsonElement e : json.getAsJsonArray("profiles")) {
                    JsonObject rule = e.getAsJsonObject();
                    if (!rule.has("model") || !rule.has("texture") || !rule.has("left")) continue;
                    PROFILES.put(modelId(rule.get("model").getAsString(), "minecraft"),
                            new Profile(rule.get("texture").getAsString(),
                            Attachment.valueOf(rule.get("attachment").getAsString()), rule.getAsJsonObject("left")));
                }
            }
        } catch (Exception ignored) {}
    }
    private static boolean matchesTexture(JsonObject json, String expected) {
        if (!json.has("textures")) return false;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet())
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsString().equals(expected)) return true;
        return false;
    }
    /** Resolve inherited geometry/displays together; keep JSON only during the
     * first classification, not in the bounded runtime caches. */
    private static JsonObject effectiveModel(ResourceManager manager, Identifier model,
            Set<Identifier> visited) throws Exception {
        if (visited.size() >= 16 || !visited.add(model)) return new JsonObject();
        boolean cit = model.getPath().startsWith("optifine/cit/") || model.getPath().startsWith("citresewn/cit/");
        Identifier file = new Identifier(model.getNamespace(), (cit ? "" : "models/") + model.getPath() + ".json");
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) return new JsonObject();
        JsonObject json;
        try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!json.has("parent") || json.get("parent").getAsString().startsWith("builtin/")) return json;
        JsonObject parent = effectiveModel(manager, modelId(json.get("parent").getAsString(), "minecraft"), visited);
        for (String field : List.of("textures", "display")) {
            JsonObject merged = new JsonObject();
            if (parent.has(field)) for (var e : parent.getAsJsonObject(field).entrySet()) merged.add(e.getKey(), e.getValue());
            if (json.has(field)) for (var e : json.getAsJsonObject(field).entrySet()) merged.add(e.getKey(), e.getValue());
            json.add(field, merged);
        }
        if ((!json.has("elements") || json.getAsJsonArray("elements").size() == 0) && parent.has("elements"))
            json.add("elements", parent.get("elements"));
        return json;
    }
    private static ModelInfo modelInfo(ResourceManager manager, Identifier model) {
        ModelInfo cached = MODEL_CACHE.get(model);
        if (cached != null) return cached;
        ModelInfo result = EMPTY;
        try {
            JsonObject json = effectiveModel(manager, model, new HashSet<>());
            if (json.size() > 0) {
                List<OverrideModel> overrides = new ArrayList<>();
                if (json.has("overrides")) for (JsonElement e : json.getAsJsonArray("overrides")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("predicate") || !o.has("model")) continue;
                    JsonObject predicate = o.getAsJsonObject("predicate");
                    // Dynamic pulling/charged predicates remain with Minecraft.
                    if (predicate.size() == 1 && predicate.has("custom_model_data"))
                        overrides.add(new OverrideModel(predicate.get("custom_model_data").getAsFloat(),
                                modelId(o.get("model").getAsString(), "minecraft")));
                }
                Identifier parent = null;
                if (json.has("parent") && !json.get("parent").getAsString().startsWith("builtin/"))
                    parent = modelId(json.get("parent").getAsString(), "minecraft");
                JsonObject display = json.has("display") ? json.getAsJsonObject("display") : new JsonObject();
                boolean hasLeft = display.has("thirdperson_lefthand");
                JsonObject left = hasLeft ? display.getAsJsonObject("thirdperson_lefthand") : new JsonObject();
                JsonObject right = display.has("thirdperson_righthand")
                        ? display.getAsJsonObject("thirdperson_righthand") : new JsonObject();
                float[] t = vector(left, "translation", 0F);
                boolean elements = json.has("elements") && json.getAsJsonArray("elements").size() > 0;
                Attachment hint = textureHint(json, model);
                loadProfiles(manager);
                Profile profile = PROFILES.get(model);
                // Preserve audited pack placements only while the actual texture
                // and left display still match. Reused IDs in another pack are safe.
                if (profile != null && hasLeft && sameDisplay(left, profile.left)
                        && (matchesTexture(json, profile.texture) || DYNAMIX_MODEL_IDS.contains(model)))
                    hint = profile.attachment;
                result = new ModelInfo(parent, List.copyOf(overrides), hint, elements, hasLeft,
                        sameDisplay(left, right), t[0], t[1], t[2], enlargedLeft(left, right),
                        elements && hasLeft && hasHandContact(json.getAsJsonArray("elements"), left));
            }
        } catch (Exception ignored) {}
        // Cache only compact classification metadata, never the voxel geometry.
        MODEL_CACHE.put(model, result);
        return result;
    }

    private static boolean hasAny(String name, String... markers) {
        for (String marker : markers) if (name.contains(marker)) return true;
        return false;
    }
    private static Attachment textureHint(JsonObject json, Identifier model) {
        StringBuilder names = new StringBuilder(" ").append(model.getPath().toLowerCase(java.util.Locale.ROOT));
        if (!json.has("textures")) {
            String name = names.toString();
            if (hasAny(name, "cloak", "cape", "mantle", "poncho", "dress", "gown", "bridal", "wedding", "robe", "kimono", "wing", "backpack", "jetpack", "foxtail", "cattail")) return Attachment.BODY;
            return Attachment.NONE;
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
            JsonElement e = entry.getValue();
            if (e.isJsonPrimitive()) {
                String path = e.getAsString().toLowerCase(java.util.Locale.ROOT);
                names.append(' ').append(path.substring(path.lastIndexOf('/') + 1));
            }
        }
        String name = names.toString();
        if (name.contains("knife")) return Attachment.KNIFE;
        if (hasAny(name, "umbrella", "parasol", " umb ") || name.endsWith(" umb")) return Attachment.UMBRELLA;
        if (hasAny(name, "watch", "clock", "clok", "wrist", "bracelet", "bracle", "bangle", "ringbox")) return Attachment.HAND;
        if (hasAny(name, "bodycam", "backpack", "bagpack", "jetpack", "wedding_veil", "veil", "dress", "gown", "bridal", "wedding",
                "cloak", "cape", "mantle", "poncho", "robe", "kimono", "backwear", "back_accessory", "wing", "foxtail", "cattail",
                "tailjoffery", "_tail", "woodgravecross", "ironpaws", "ironclaws", "spidermanclaws")) return Attachment.BODY;
        if (hasAny(name, "camera", "phone", "musket", "briefcase", "handbag", "purse", "lunchbox",
                "studiocam", "newnewcam", "violin_bow", "sketchbook", "journal", "tarot", "laptop", "powerbank", "wedding_bouquet")) return Attachment.HAND;
        if (hasAny(name, "sword", "katana", "_blade", "scythe", "sycle", "scycle", "_kosa", "posoh", "posox",
                "staff", "staf", "spear", "trident", "shield", "hammer", "pickaxe", "_axe", "guitar", "ukelele", "violin",
                "surfboard", "skateboard", "hoverboard")) return Attachment.BACK_ITEM;
        return Attachment.NONE;
    }

    private static float[] vector(JsonObject json, String name, float fallback) {
        float[] v = {fallback, fallback, fallback};
        if (json.has(name)) {
            JsonArray a = json.getAsJsonArray(name);
            for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        }
        return v;
    }
    private static boolean sameDisplay(JsonObject left, JsonObject right) {
        if (right.size() == 0) return false;
        for (String channel : List.of("rotation", "translation", "scale")) {
            float[] a = vector(left, channel, channel.equals("scale") ? 1F : 0F);
            float[] b = vector(right, channel, channel.equals("scale") ? 1F : 0F);
            for (int i = 0; i < 3; i++) if (Math.abs(a[i] - b[i]) > .1F) return false;
        }
        return true;
    }
    private static boolean enlargedLeft(JsonObject left, JsonObject right) {
        if (right.size() == 0) return false;
        float[] a = vector(left, "scale", 1F), b = vector(right, "scale", 1F);
        float l = Math.max(Math.abs(a[0]), Math.max(Math.abs(a[1]), Math.abs(a[2])));
        float r = Math.max(Math.abs(b[0]), Math.max(Math.abs(b[1]), Math.abs(b[2])));
        return r > 1.0E-5F && l >= .75F && l >= r * 1.75F;
    }
    private static void rotate(float[] v, int axis, float degrees) {
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        double radians = Math.toRadians(degrees), c = Math.cos(radians), s = Math.sin(radians);
        float x = v[a], y = v[b];v[a] = (float)(x*c-y*s);v[b] = (float)(x*s+y*c);
    }
    private static boolean hasHandContact(JsonArray elements, JsonObject left) {
        float[] translation = vector(left, "translation", 0F), rotation = vector(left, "rotation", 0F), scale = vector(left, "scale", 1F);
        float minimumScale = Math.min(Math.abs(scale[0]), Math.min(Math.abs(scale[1]), Math.abs(scale[2])));
        if (minimumScale < 1.0E-5F) return false;
        // Vanilla's left wrist in item-grip space, in model pixels.
        float[] wrist = {1F + translation[0], -2F - translation[1], -translation[2]};
        rotate(wrist, 0, -rotation[0]);rotate(wrist, 1, rotation[1]);rotate(wrist, 2, rotation[2]);
        for (int i = 0; i < 3; i++) wrist[i] = 8F + wrist[i] / scale[i];
        for (JsonElement e : elements) {
            if (!e.isJsonObject()) continue;
            JsonObject box = e.getAsJsonObject();
            if (!box.has("from") || !box.has("to")) continue;
            float[] point = wrist.clone(), from = vector(box, "from", 0F), to = vector(box, "to", 0F);
            int boxAxis = 1; float boxAngle = 0F, compensation = 1F;
            if (box.has("rotation")) {
                JsonObject r = box.getAsJsonObject("rotation");float[] origin = vector(r, "origin", 0F);
                String axis = r.has("axis") ? r.get("axis").getAsString() : "y";
                for (int i = 0; i < 3; i++) point[i] -= origin[i];
                boxAxis = axis.equals("x") ? 0 : axis.equals("z") ? 2 : 1;
                boxAngle = r.get("angle").getAsFloat();
                rotate(point, boxAxis, -boxAngle);
                if (r.has("rescale") && r.get("rescale").getAsBoolean()) {
                    compensation = (float)Math.cos(Math.toRadians(boxAngle));
                    for (int i = 0; i < 3; i++) if (i != boxAxis) point[i] *= compensation;
                }
                for (int i = 0; i < 3; i++) point[i] += origin[i];
            }
            float distance = 0F;
            for (int i = 0; i < 3; i++) {
                point[i] -= Math.max(from[i], Math.min(to[i], point[i]));
            }
            // Measure in rendered pixels, including nonuniform scale and the
            // element's rotation/rescale, rather than an optimistic minimum scale.
            for (int i = 0; i < 3; i++) if (i != boxAxis) point[i] /= compensation;
            rotate(point, boxAxis, boxAngle);
            for (int i = 0; i < 3; i++) distance += point[i] * point[i] * scale[i] * scale[i];
            if (distance <= 2.25F * 2.25F) return true;
        }
        return false;
    }
}
