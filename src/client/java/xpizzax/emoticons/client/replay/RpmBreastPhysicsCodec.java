package xpizzax.emoticons.client.replay;

import com.google.gson.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** RPM 7.1 has two spring objects. Store their scalar state, never player/world references. */
final class RpmBreastPhysicsCodec {
    private static final String CODEC="rpm-7.1-physics-v1";
    private record Part(Field owner,Field[] values) {}
    private record Layout(Part[] parts,String signature) {}
    private static final ClassValue<Layout> LAYOUTS=new ClassValue<>() {
        @Override protected Layout computeValue(Class<?> model) {
            try {
                List<Part> parts=new ArrayList<>();StringBuilder signature=new StringBuilder(model.getName());
                for(Field owner:sortedFields(model)) {
                    if(Modifier.isStatic(owner.getModifiers()) || !owner.getType().getName().startsWith("RPMC."))continue;
                    Field[] values=Arrays.stream(sortedFields(owner.getType()))
                            .filter(f->!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                            .filter(f->f.getType()==float.class || f.getType()==int.class || f.getType()==boolean.class)
                            .toArray(Field[]::new);
                    if(values.length==0)continue;
                    owner.setAccessible(true);signature.append('|').append(owner.getName());
                    for(Field value:values) {
                        value.setAccessible(true);signature.append('|').append(value.getName()).append(':').append(value.getType().getName());
                    }
                    parts.add(new Part(owner,values));
                }
                if(parts.size()!=2)throw new IllegalStateException("Unsupported RPM breast spring layout");
                String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(signature.toString().getBytes(StandardCharsets.UTF_8)));
                return new Layout(parts.toArray(Part[]::new),digest);
            } catch(java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("Cannot inspect RPM breast physics",e);
            }
        }
    };
    private RpmBreastPhysicsCodec() {}
    private static boolean isSize(Field field){
        return field.getName().equals("GBRPMC\u200e\u200b\u200b\u200f\u200c\u200b\u200b\u200b\u200b\u200b")
                || field.getName().equals("GBRPMC\u200d\u200b\u200b\u200f\u200c\u200b\u200b\u200b\u200b\u200b");
    }
    static boolean hasSize(Object model)throws IllegalAccessException {
        for(Part part:LAYOUTS.get(model.getClass()).parts){
            Object spring=part.owner.get(model);
            for(Field field:part.values)if(isSize(field)&&Math.abs(field.getFloat(spring))>1e-7F)return true;
        }
        return false;
    }

    /** RPM 7.1 constructors leave both interpolated sizes at zero until live ticks.
     * Config-only recordings have no such ticks when paused after a seek. Seed
     * the two size endpoints from RPM's first encoded config float, then freeze
     * the complete neutral spring state. This never runs for an absent/Male sample. */
    static Snapshot neutral(Object model,byte[] config)throws Exception {
        if(config.length<4)throw new IOException("Missing RPM breast size");
        float size=java.nio.ByteBuffer.wrap(config).getFloat();
        if(!Float.isFinite(size)||size<0||size>16)throw new IOException("Invalid RPM breast size");
        Layout layout=LAYOUTS.get(model.getClass());
        for(Part part:layout.parts){
            Object spring=part.owner.get(model);
            for(Field value:part.values)if(value.getType()==float.class)value.setFloat(spring,0F);
            for(String name:new String[]{"GBRPMC\u200e\u200b\u200b\u200f\u200c\u200b\u200b\u200b\u200b\u200b","GBRPMC\u200d\u200b\u200b\u200f\u200c\u200b\u200b\u200b\u200b\u200b"}){
                Field field=spring.getClass().getDeclaredField(name);field.setAccessible(true);field.setFloat(spring,size);
            }
        }
        return decode(model,capture(model));
    }

    private static Field[] sortedFields(Class<?> type) {
        Field[] fields=type.getDeclaredFields();Arrays.sort(fields,Comparator.comparing(Field::getName));return fields;
    }

    /** Reused primitive storage: unchanged ticks create no JSON tree or signature string. */
    static final class Recording {
        private final Object model;
        private final Layout layout;
        private final Object[] springs;
        private final long[][] values;
        private boolean initialized;
        Recording(Object model)throws IllegalAccessException {
            this.model=model;layout=LAYOUTS.get(model.getClass());
            springs=new Object[layout.parts.length];values=new long[layout.parts.length][];
            for(int p=0;p<springs.length;p++) {
                springs[p]=layout.parts[p].owner.get(model);values[p]=new long[layout.parts[p].values.length];
            }
        }
        boolean isFor(Object candidate) {return model==candidate;}
        boolean hasSize(){
            for(int p=0;p<springs.length;p++)for(int i=0;i<values[p].length;i++)
                if(isSize(layout.parts[p].values[i])&&Math.abs(Float.intBitsToFloat((int)values[p][i]))>1e-7F)return true;
            return false;
        }
        boolean refresh()throws Exception {
            boolean changed=!initialized;
            for(int p=0;p<springs.length;p++)for(int i=0;i<values[p].length;i++) {
                Field field=layout.parts[p].values[i];long bits;
                if(field.getType()==float.class) {
                    float value=field.getFloat(springs[p]);
                    if(!Float.isFinite(value))throw new IOException("Non-finite RPM breast physics");
                    bits=Float.floatToRawIntBits(value);
                } else if(field.getType()==int.class)bits=field.getInt(springs[p]);
                else bits=field.getBoolean(springs[p])?1:0;
                // RPM's counters/flags can change even when both visible springs
                // are still. Keep them in snapshots, but create a new sample only
                // when a float pose/size changes (or the config/presence changes).
                if(field.getType()==float.class&&bits!=values[p][i])changed=true;
                values[p][i]=bits;
            }
            initialized=true;return changed;
        }
        JsonObject toJson() {
            JsonArray parts=new JsonArray();
            for(int p=0;p<springs.length;p++) {
                JsonArray part=new JsonArray();
                for(int i=0;i<values[p].length;i++) {
                    Class<?> type=layout.parts[p].values[i].getType();long bits=values[p][i];
                    if(type==float.class)part.add(Float.intBitsToFloat((int)bits));
                    else if(type==int.class)part.add((int)bits);
                    else part.add(bits!=0);
                }
                parts.add(part);
            }
            JsonObject state=new JsonObject();state.addProperty("codec",CODEC);state.addProperty("layout",layout.signature);
            state.add("parts",parts);return state;
        }
    }

    static JsonObject capture(Object model) throws Exception {
        Layout layout=LAYOUTS.get(model.getClass());JsonArray parts=new JsonArray();
        for(Part part:layout.parts) {
            Object spring=part.owner.get(model);JsonArray values=new JsonArray();
            for(Field field:part.values) {
                if(field.getType()==float.class) {
                    float value=field.getFloat(spring);
                    if(!Float.isFinite(value))throw new IOException("Non-finite RPM breast physics");
                    values.add(value);
                } else if(field.getType()==int.class)values.add(field.getInt(spring));
                else values.add(field.getBoolean(spring));
            }
            parts.add(values);
        }
        JsonObject state=new JsonObject();state.addProperty("codec",CODEC);state.addProperty("layout",layout.signature);
        state.add("parts",parts);return state;
    }

    /** Decode once per recorded sample; applying it on an accessor call makes no new objects. */
    static Snapshot decode(Object model,JsonObject state) throws Exception {
        Layout layout=LAYOUTS.get(model.getClass());
        if(!CODEC.equals(state.get("codec").getAsString()) || !layout.signature.equals(state.get("layout").getAsString()))
            throw new IOException("Unsupported RPM breast physics recording");
        JsonArray parts=state.getAsJsonArray("parts");
        if(parts.size()!=layout.parts.length)throw new IOException("Invalid RPM breast spring count");
        List<Assignment> assignments=new ArrayList<>();
        for(int p=0;p<layout.parts.length;p++) {
            Part part=layout.parts[p];Object spring=part.owner.get(model);JsonArray values=parts.get(p).getAsJsonArray();
            if(values.size()!=part.values.length)throw new IOException("Invalid RPM breast value count");
            for(int i=0;i<part.values.length;i++) {
                Field field=part.values[i];JsonPrimitive value=values.get(i).getAsJsonPrimitive();Object scalar;
                if(field.getType()==boolean.class) {
                    if(!value.isBoolean())throw new IOException("Invalid RPM breast flag");
                    scalar=value.getAsBoolean();
                } else if(field.getType()==float.class) {
                    if(!value.isNumber() || !Float.isFinite(value.getAsFloat()))throw new IOException("Invalid RPM breast float");
                    scalar=value.getAsFloat();
                } else {
                    if(!value.isNumber())throw new IOException("Invalid RPM breast integer");
                    scalar=value.getAsBigDecimal().intValueExact();
                }
                assignments.add(new Assignment(spring,field,scalar));
            }
        }
        return new Snapshot(assignments.toArray(Assignment[]::new));
    }

    private record Assignment(Object spring,Field field,Object value) {}
    static final class Snapshot {
        private final Assignment[] assignments;
        private Snapshot(Assignment[] assignments) {this.assignments=assignments;}
        void apply() throws IllegalAccessException {
            for(Assignment assignment:assignments)assignment.field.set(assignment.spring,assignment.value);
        }
    }
}
