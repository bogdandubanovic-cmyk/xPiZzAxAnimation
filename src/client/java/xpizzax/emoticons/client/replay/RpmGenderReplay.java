package xpizzax.emoticons.client.replay;

import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Optional integration with ReplayMod 2.6.15 and the supplied RPM Client 7.1.0.
 * ReplayFile owns the persistent entry; no global cache is used as recorded state.
 */
public final class RpmGenderReplay {
    private static final String ENTRY = "xpizzax/rpm_gender_v1.json";
    private static final Gson GSON = new Gson();
    private static volatile Object recorder;
    private static final Map<String, List<JsonObject>> recording = new HashMap<>();
    private static final Map<UUID, RecordingPlayer> recordingPlayers = new HashMap<>();
    private static final Map<String, List<JsonObject>> wildfireRecording = new HashMap<>();
    private static final Map<String, String> wildfireLastStates = new HashMap<>();
    private static final Map<String, List<JsonObject>> wildfirePlayback = new HashMap<>();
    private static final Map<String, JsonObject> wildfireSelected = new HashMap<>();
    private static final Map<String, Optional<?>> wildfireRestored = new HashMap<>();
    private static Object playbackFile;
    private static JsonObject playbackRoot;
    private static boolean recoveredDirty;
    private static final Map<String, List<JsonObject>> playback = new HashMap<>();
    private static final Map<String, JsonObject> selected = new HashMap<>();
    private static final Map<String, Optional<?>> restored = new HashMap<>();
    private static final Map<String, RpmBreastPhysicsCodec.Snapshot> restoredPhysics = new HashMap<>();
    private static final Map<UUID, String> playbackIds = new HashMap<>();
    private static boolean warned;
    private static boolean contextValid;
    private static Object contextHandler,contextFile;
    private static long contextTime,contextRefreshes;
    private static final ClassValue<Optional<Method>> RPM_GETTERS=new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            try { return Optional.of(type.getMethod("gender$getBreastModel")); }
            catch(NoSuchMethodException absent) { return Optional.empty(); }
        }
    };
    private static final class RecordingPlayer {
        final String uuid;
        boolean initialized,present,resolved;
        byte[] config;
        String encoded;
        RpmBreastPhysicsCodec.Recording physics;
        RecordingPlayer(UUID id) {uuid=id.toString();}
    }
    private static final ClassValue<Method> RECORDER_TIMES=new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {return type.getMethod("getCurrentDuration");}
            catch(NoSuchMethodException e) {throw new IllegalStateException(e);}
        }
    };
    private static final class ReplayApi {
        static final Field INSTANCE;
        static final Method HANDLER,FILE,SENDER,TIME;
        static {
            Field instance=null;Method handler=null,file=null,sender=null,time=null;
            try {
                Class<?> api=Class.forName("com.replaymod.replay.ReplayModReplay");
                Class<?> handle=Class.forName("com.replaymod.replay.ReplayHandler");
                instance=api.getField("instance");handler=api.getMethod("getReplayHandler");
                file=handle.getMethod("getReplayFile");sender=handle.getMethod("getReplaySender");
                time=Class.forName("com.replaymod.replay.ReplaySender").getMethod("currentTimeStamp");
            } catch(ReflectiveOperationException | LinkageError ignored) {}
            INSTANCE=instance;HANDLER=handler;FILE=file;SENDER=sender;TIME=time;
        }
    }
    private static final class WildfirePresence {
        static final boolean INSTALLED;
        static {
            boolean present;
            try {Class.forName("com.wildfire.main.WildfireGender",false,RpmGenderReplay.class.getClassLoader());present=true;}
            catch(ClassNotFoundException | LinkageError absent) {present=false;}
            INSTALLED=present;
        }
    }
    private RpmGenderReplay() {}

    public static synchronized void begin(Object listener) {
        invalidateContext();
        recorder = listener;
        recording.clear();
        recordingPlayers.clear();
        wildfireRecording.clear(); wildfireLastStates.clear();
    }

    public static void tick(MinecraftClient client) {
        invalidateContext();
        if (client.world == null || recorder == null || handler() != null) return;
        for (PlayerEntity player : client.world.getPlayers()) {
            record(player);
            recordWildfire(player);
        }
    }

    private static synchronized void record(PlayerEntity player) {
        if (recorder == null) return;
        try {
            Method getter=RPM_GETTERS.get(player.getClass()).orElse(null);
            if(getter==null)return;
            Object result = getter.invoke(player);
            if (!(result instanceof Optional<?> state)) return;
            recordModel(player.getUuid(),state.orElse(null),false);
        } catch (Throwable error) { warn(error); }
    }

    /** RPM's packet handler distinguishes explicit Male/removal from cache clearing.
     * Capture profiles even when the corresponding player is not currently loaded. */
    public static synchronized void profileReceived(Object id,Object model) {
        if (!(id instanceof UUID uuid) || recorder==null || handler()!=null) return;
        try {recordModel(uuid,model,true);}catch(Throwable error){warn(error);}
    }

    private static void recordModel(UUID id,Object model,boolean explicit)throws Exception {
            boolean present=model!=null;
            RecordingPlayer previous=recordingPlayers.computeIfAbsent(id,RecordingPlayer::new);
            // A cache reset/disconnect is not a server instruction to select Male.
            if(!present && !explicit && previous.resolved)return;
            boolean resolved=present || explicit;
            boolean changed=!previous.initialized || previous.present!=present || previous.resolved!=resolved;
            if (present) {
                if (!model.getClass().getName().equals(EXPECTED_MODEL)) return;
                byte[] bytes = serialize(model);
                if(!Arrays.equals(bytes,previous.config)) {
                    previous.config=bytes;previous.encoded=Base64.getEncoder().encodeToString(bytes);changed=true;
                }
                try {
                    if(previous.physics==null || !previous.physics.isFor(model)) {
                        previous.physics=new RpmBreastPhysicsCodec.Recording(model);changed=true;
                    }
                    changed=previous.physics.refresh() || changed;
                } catch(Exception physicsError) {
                    // Visibility/config remains recordable if a spring cannot be captured.
                    previous.physics=null;warn(physicsError);
                }
            } else {
                previous.physics=null;
            }
            if(!changed)return;
            JsonObject sample=new JsonObject();
            if(present) {
                sample.addProperty("model",EXPECTED_MODEL);sample.addProperty("data",previous.encoded);
                if(previous.physics!=null && previous.physics.hasSize())sample.add("physics",previous.physics.toJson());
            }
            if(!resolved)sample.addProperty("pending",true);
            long time = ((Number) RECORDER_TIMES.get(recorder.getClass()).invoke(recorder)).longValue();
            sample.addProperty("time", Math.max(0L, time));
            List<JsonObject> states=recording.computeIfAbsent(previous.uuid, key -> new ArrayList<>());
            if(resolved && previous.initialized && !previous.resolved && !states.isEmpty()) {
                // The profile arrives after the join packet. Seed the initial
                // appearance at the first observed timestamp instead of replaying
                // an empty, not-yet-loaded module cache as an intentional Male.
                JsonObject initial=sample.deepCopy();initial.remove("physics");
                initial.addProperty("time",states.get(0).get("time").getAsLong());
                states.clear();states.add(initial);
            }
            if(!states.isEmpty() && states.get(states.size()-1).get("time").getAsLong()==Math.max(0L,time))
                states.set(states.size()-1,sample);
            else states.add(sample);
            previous.initialized=true;previous.present=present;previous.resolved=resolved;
    }

    private static synchronized void recordWildfire(PlayerEntity player) {
        if (recorder == null || !WildfirePresence.INSTALLED) return;
        try {
            JsonObject sample = WildfireReplayCodec.capture(player.getUuid());
            if (sample == null) return; // Wildfire is not installed.
            String uuid = player.getUuid().toString();
            String signature = sample.toString();
            if (signature.equals(wildfireLastStates.get(uuid))) return;
            long time = ((Number) RECORDER_TIMES.get(recorder.getClass()).invoke(recorder)).longValue();
            sample.addProperty("time", Math.max(0L, time));
            wildfireRecording.computeIfAbsent(uuid, key -> new ArrayList<>()).add(sample);
            wildfireLastStates.put(uuid, signature);
        } catch (Throwable error) { warn(error); }
    }

    /** null = no override, empty = recorded absence, present = replay-only profile. */
    public static synchronized Optional<?> playbackWildfire(PlayerEntity player) {
        updateContext();Object handler = contextHandler;
        if (handler == null) { clearPlayback(); return null; }
        try {
            Object file = contextFile;
            if (file != playbackFile) load(file);
            String uuid=playbackIds.get(player.getUuid());
            List<JsonObject> states = wildfirePlayback.get(uuid);
            if (states == null) return null;
            long time = contextTime;
            JsonObject sample = select(states, time);
            if (sample == null) sample = states.isEmpty() ? null : states.get(0);
            if (sample == null) return Optional.empty();
            if (wildfireSelected.get(uuid) == sample) return wildfireRestored.get(uuid);
            Optional<?> state = sample.has("data")
                    ? Optional.of(WildfireReplayCodec.restore(player.getUuid(), sample)) : Optional.empty();
            wildfireSelected.put(uuid, sample); wildfireRestored.put(uuid, state);
            String gender = state.isPresent()
                    ? String.valueOf(state.get().getClass().getMethod("getGender").invoke(state.get())) : "ABSENT";
            System.out.println("[xPiZzAxAnimation Wildfire Replay] uuid=" + uuid + " replayTime=" + time
                    + " sampleTime=" + sample.get("time").getAsLong() + " gender=" + gender);
            return state;
        } catch (Throwable error) { warn(error); return null; }
    }

    public static boolean hideWildfireBreasts(PlayerEntity player) {
        return hideWildfireBreasts(playbackWildfire(player));
    }

    /** Check the already selected render sample without resolving replay time twice. */
    public static boolean hideWildfireBreasts(Optional<?> profile) {
        if (profile == null) return false;
        if (profile.isEmpty()) return true;
        try {
            Object gender = profile.get().getClass().getMethod("getGender").invoke(profile.get());
            return !(Boolean) gender.getClass().getMethod("canHaveBreasts").invoke(gender);
        } catch (ReflectiveOperationException error) { warn(error); return false; }
    }

    private static JsonObject select(List<JsonObject> states, long time) {
        int low = 0, high = states.size() - 1, index = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (states.get(middle).get("time").getAsLong() <= time) {
                index = middle; low = middle + 1;
            } else high = middle - 1;
        }
        return index < 0 ? null : states.get(index);
    }

    /** Queue before PacketListener queues its final save/close work on the SAME executor. */
    public static synchronized void finish(Object listener) {
        if (recorder != listener) return;
        recorder = null;
        if (recording.isEmpty() && wildfireRecording.isEmpty()) {recordingPlayers.clear();return;}
        try {
            Object replayFile = field(listener, "replayFile");
            ExecutorService service = (ExecutorService) field(listener, "saveService");
            JsonObject root = new JsonObject(); root.addProperty("format", 3);
            root.addProperty("recordedBy","RPM Roleplay");root.add("players", GSON.toJsonTree(recording));
            root.add("wildfirePlayers", GSON.toJsonTree(wildfireRecording));
            byte[] data = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            service.execute(() -> {
                try (OutputStream out = (OutputStream) Class.forName("com.replaymod.replaystudio.replay.ReplayFile").getMethod("write", String.class).invoke(replayFile, ENTRY)) {
                    out.write(data);System.out.println("[xPiZzAxAnimation] Breast state saved inside Replay ("+data.length+" bytes)");
                } catch (Throwable error) { warn(error); }
            });
        } catch (Throwable error) { warn(error); }
        finally { recording.clear(); recordingPlayers.clear(); wildfireRecording.clear(); wildfireLastStates.clear(); }
    }

    /** null means no recorded override; Optional.empty means recorded absence of breasts. */
    public static synchronized Optional<?> playbackState(PlayerEntity player) {
        updateContext();Object handler = contextHandler;
        if (handler == null) {
            clearPlayback();
            return null;
        }
        try {
            Object file = contextFile;
            if (file != playbackFile) load(file);
            String uuid=playbackIds.get(player.getUuid());
            List<JsonObject> states = playback.get(uuid);
            if (states == null) return null; // Older recordings remain usable.
            long time = contextTime;
            JsonObject sample = select(states, time);
            // The first client tick can be later than replay time zero. Keep
            // its initial state while exporting the leading frames instead
            // of falling back to RPM's current live profile.
            if (sample == null) sample = states.isEmpty() ? null : states.get(0);
            if (sample == null) return Optional.empty();
            if(selected.get(uuid)!=sample) {
                JsonObject previous=selected.get(uuid);Optional<?> old=restored.get(uuid);
                boolean sameConfig=previous!=null && sample.has("data") && previous.has("data")
                        && sample.get("model").equals(previous.get("model")) && sample.get("data").equals(previous.get("data"));
                Optional<?> state=sample.has("data")
                        ? (sameConfig && old!=null && old.isPresent()?old:Optional.of(deserialize(sample))) : Optional.empty();
                RpmBreastPhysicsCodec.Snapshot physics=null;
                if(state.isPresent()&&sample.has("physics"))try {
                    physics=RpmBreastPhysicsCodec.decode(state.get(),sample.getAsJsonObject("physics"));
                    physics.apply();
                    // A newly received RPM profile has zero spring sizes until
                    // the first live tick. Such a snapshot must remain visible
                    // after seeking into that exact initial frame.
                    if(!RpmBreastPhysicsCodec.hasSize(state.get()))physics=null;
                } catch(Exception physicsError) {warn(physicsError);}
                if(state.isPresent()&&physics==null)try {
                    physics=RpmBreastPhysicsCodec.neutral(state.get(),Base64.getDecoder().decode(sample.get("data").getAsString()));
                }catch(Exception physicsError){warn(physicsError);}
                selected.put(uuid,sample);restored.put(uuid,state);
                if(physics==null)restoredPhysics.remove(uuid);else restoredPhysics.put(uuid,physics);
            }
            // RPM may tick its spring while Replay is paused/exporting. Reapply the
            // recorded scalar values before each use; never depend on the live profile.
            RpmBreastPhysicsCodec.Snapshot physics=restoredPhysics.get(uuid);
            if(physics!=null)try{physics.apply();}catch(Exception physicsError){restoredPhysics.remove(uuid);warn(physicsError);}
            Optional<?> state=restored.get(uuid);
            return state;
        } catch (Throwable error) { warn(error); return Optional.empty(); }
    }

    /** Called at every RPM accessor return, even after Replay reconstructs player entities. */
    public static synchronized Optional<?> fromLiveModel(Optional<?> live,Object entity) {
        if(!(entity instanceof PlayerEntity player))return live;
        Optional<?> recorded=playbackState(player);
        if(recorded!=null)return recorded; // Includes explicit recorded Male.
        // Older recordings may have no RPM entry. Preserve the actual profile
        // visible on first opening before RPM clears its session map on a seek.
        // Never infer a Female when the file already contains an absence sample.
        if(contextHandler!=null && contextFile!=null && live.isPresent())try {
            Object model=live.get();
            if(!EXPECTED_MODEL.equals(model.getClass().getName()))return live;
            String uuid=player.getUuid().toString();
            if(!playback.containsKey(uuid)) {
                JsonObject sample=new JsonObject();sample.addProperty("time",0);
                sample.addProperty("model",EXPECTED_MODEL);sample.addProperty("data",Base64.getEncoder().encodeToString(serialize(model)));
                if(RpmBreastPhysicsCodec.hasSize(model))sample.add("physics",RpmBreastPhysicsCodec.capture(model));
                sample.addProperty("recoveredVisibleProfile",true);
                playback.put(uuid,new ArrayList<>(List.of(sample)));playbackIds.put(player.getUuid(),uuid);recoveredDirty=true;
                System.out.println("[xPiZzAxAnimation] Preserved visible RPM profile for this Replay");
                return playbackState(player);
            }
        }catch(Throwable error){warn(error);}
        return live;
    }
    /** Called before RPM touches its disposable live module cache. */
    public static Optional<?> replayOverride(Object entity) {
        return entity instanceof PlayerEntity player?playbackState(player):null;
    }

    /** One Replay lookup per frame/tick instead of one reflective chain per feature/player. */
    public static synchronized void invalidateContext(){contextValid=false;}
    public static synchronized void replayClosed(){saveRecovered(true);replayEnded();}
    /** ReplayHandler has stopped its senders and is about to save the archive. */
    public static synchronized void prepareReplayClose(){saveRecovered(false);}
    public static synchronized void replayEnded(){contextValid=false;contextHandler=null;contextFile=null;clearPlayback();}
    private static void updateContext(){
        if(contextValid)return;contextValid=true;contextRefreshes++;
        contextHandler=handler();contextFile=null;contextTime=0;
        if(contextHandler!=null)try{
            contextFile=ReplayApi.FILE.invoke(contextHandler);
            contextTime=((Number)ReplayApi.TIME.invoke(ReplayApi.SENDER.invoke(contextHandler))).longValue();
        }catch(ReflectiveOperationException|RuntimeException e){contextHandler=null;warn(e);}
    }

    private static void load(Object file) throws Exception {
        clearPlayback(); playbackFile = file;
        Object optional = Class.forName("com.replaymod.replaystudio.replay.ReplayFile").getMethod("get", String.class).invoke(file, ENTRY);
        if (!(Boolean) Class.forName("com.replaymod.replaystudio.lib.guava.base.Optional").getMethod("isPresent").invoke(optional)) return;
        try (InputStream in = (InputStream) Class.forName("com.replaymod.replaystudio.lib.guava.base.Optional").getMethod("get").invoke(optional);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            playbackRoot=root;
            if (!root.has("format")) return;
            int format = root.get("format").getAsInt();
            if (format < 1 || format > 3) return;
            loadPlayers(root, "players", playback);
            if (format >= 2) loadPlayers(root, "wildfirePlayers", wildfirePlayback);
            System.out.println("[xPiZzAxAnimation] Loaded recorded RPM breast profiles: "+playback.size());
        }
    }

    private static void loadPlayers(JsonObject root, String key, Map<String, List<JsonObject>> target) {
        if (!root.has(key)) return;
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            playbackIds.put(UUID.fromString(entry.getKey()),entry.getKey());
            List<JsonObject> samples = new ArrayList<>();
            for (JsonElement value : entry.getValue().getAsJsonArray()) samples.add(value.getAsJsonObject());
            samples.sort(Comparator.comparingLong(value -> value.get("time").getAsLong()));
            target.put(entry.getKey(), samples);
        }
    }

    /** RPM's superclass config has two ByteBuf methods: encoder and decoder.
     * An empty input cannot be decoded; the encoder is identified by written bytes.
     * Store only config, not its live spring/animation state or player references. */
    private static final ClassValue<ConfigEncoder> CONFIG_ENCODERS=new ClassValue<>() {
        @Override protected ConfigEncoder computeValue(Class<?> model) {return new ConfigEncoder(model.getSuperclass());}
    };
    private static final class ConfigEncoder {
        final Method[] candidates;
        final ThreadLocal<ByteBuf> buffers=ThreadLocal.withInitial(Unpooled::buffer);
        volatile Method encoder;
        ConfigEncoder(Class<?> config) {
            candidates=Arrays.stream(config.getDeclaredMethods()).filter(RpmGenderReplay::bufferMethod).toArray(Method[]::new);
            for(Method method:candidates)method.setAccessible(true);
        }
        byte[] encode(Object model)throws Exception {
            ByteBuf buffer=buffers.get();
            if(encoder!=null) {
                buffer.clear();encoder.invoke(model,buffer);return bytes(buffer);
            }
            for(Method method:candidates) {
                buffer.clear();
                try {
                    method.invoke(model,buffer);
                    if(buffer.writerIndex()>0) {byte[] data=bytes(buffer);encoder=method;return data;}
                } catch(InvocationTargetException ignored) {
                    // Probe only once per model class; never retry the empty decoder each tick.
                }
            }
            throw new IOException("RPM gender config encoder not found");
        }
        private static byte[] bytes(ByteBuf buffer)throws IOException {
            int size=buffer.writerIndex();
            if(size<=0 || size>1024)throw new IOException("Invalid RPM gender config length");
            byte[] data=new byte[size];buffer.getBytes(0,data);return data;
        }
    }
    private static byte[] serialize(Object model) throws Exception {
        return CONFIG_ENCODERS.get(model.getClass()).encode(model);
    }

    private static Object deserialize(JsonObject sample) throws Exception {
        String name = sample.get("model").getAsString();
        if (!name.equals(EXPECTED_MODEL)) throw new IOException("Unsupported RPM gender model version");
        byte[] data = Base64.getDecoder().decode(sample.get("data").getAsString());
        if (data.length == 0 || data.length > 1024) throw new IOException("Invalid RPM gender config length");
        Class<?> model = Class.forName(name);
        Class<?> configType = model.getSuperclass();
        for (Method method : configType.getDeclaredMethods()) {
            if (!bufferMethod(method)) continue;
            Object config = configType.getConstructor().newInstance();
            ByteBuf buffer = Unpooled.wrappedBuffer(data.clone());
            try {
                method.setAccessible(true); method.invoke(config, buffer);
                if (buffer.readerIndex() == data.length) return model.getConstructor(configType).newInstance(config);
            } catch (InvocationTargetException ignored) {
            } finally { buffer.release(); }
        }
        throw new IOException("RPM gender config decoder not found");
    }

    private static boolean bufferMethod(Method method) {
        return !Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                && Arrays.equals(method.getParameterTypes(), new Class<?>[]{ByteBuf.class});
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object handler() {
        if(ReplayApi.INSTANCE==null || ReplayApi.HANDLER==null)return null;
        try {
            Object instance = ReplayApi.INSTANCE.get(null);
            return instance == null ? null : ReplayApi.HANDLER.invoke(instance);
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    private static void clearPlayback() {
        if(playbackFile==null && playback.isEmpty() && wildfirePlayback.isEmpty())return;
        playbackFile = null;playbackRoot=null;recoveredDirty=false;playback.clear(); selected.clear(); restored.clear();restoredPhysics.clear();
        wildfirePlayback.clear(); wildfireSelected.clear(); wildfireRestored.clear();playbackIds.clear();
    }
    /** Only when leaving Replay, never file I/O in the per-frame render path. */
    private static void saveRecovered(boolean commit){
        if(!recoveredDirty || playbackFile==null)return;
        try {
            JsonObject root=playbackRoot==null?new JsonObject():playbackRoot.deepCopy();
            root.addProperty("format",3);root.addProperty("recordedBy","RPM Roleplay");
            root.add("players",GSON.toJsonTree(playback));root.add("wildfirePlayers",GSON.toJsonTree(wildfirePlayback));
            Class<?> api=Class.forName("com.replaymod.replaystudio.replay.ReplayFile");
            try(Writer writer=new OutputStreamWriter((OutputStream)api.getMethod("write",String.class).invoke(playbackFile,ENTRY),StandardCharsets.UTF_8)){
                GSON.toJson(root,writer);
            }
            if(commit)api.getMethod("save").invoke(playbackFile);
            recoveredDirty=false;
            System.out.println("[xPiZzAxAnimation] Recovered RPM profiles saved inside Replay");
        }catch(Throwable error){warn(error);}
    }
    private static void warn(Throwable error) {
        if (!warned) { warned = true; System.err.println("[xPiZzAxAnimation] Replay breast state: " + error); }
    }
    private static final String EXPECTED_MODEL = "RPMC.GBRPMC\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200c\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200f\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200f\u200c\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200d\u200c\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200c\u200e\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200b\u200b\u200c\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200f\u200f\u200d\u200c\u200c\u200b\u200b\u200b\u200b\u200b";
}
