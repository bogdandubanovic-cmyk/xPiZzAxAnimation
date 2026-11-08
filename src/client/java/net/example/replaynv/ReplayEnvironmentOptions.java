package net.example.replaynv;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Properties;

/** Fixed visual conditions, independent of the packet and animation clocks. */
public record ReplayEnvironmentOptions(boolean fixedTime, int timeTicks,
                                       Weather weather, float precipitation, float rainOpacity) {
    public enum Weather { RECORDED, CLEAR, RAIN, THUNDER }
    public static final int DAY_LENGTH = 24000;
    public static final float DEFAULT_RAIN_OPACITY = .20F;
    public static final ReplayEnvironmentOptions RECORDED =
            new ReplayEnvironmentOptions(false, 6000, Weather.RECORDED, 1F, DEFAULT_RAIN_OPACITY);

    public ReplayEnvironmentOptions(boolean fixedTime, int timeTicks, Weather weather, float precipitation) {
        this(fixedTime, timeTicks, weather, precipitation, DEFAULT_RAIN_OPACITY);
    }

    public ReplayEnvironmentOptions {
        timeTicks = Math.floorMod(timeTicks, DAY_LENGTH);
        if (weather == null) weather = Weather.RECORDED;
        precipitation = Float.isFinite(precipitation)
                ? Math.max(0F, Math.min(1F, precipitation)) : 1F;
        rainOpacity = Float.isFinite(rainOpacity)
                ? Math.max(0F, Math.min(1F, rainOpacity)) : DEFAULT_RAIN_OPACITY;
    }

    public ReplayEnvironmentOptions withTime(int ticks) {
        return new ReplayEnvironmentOptions(true, ticks, weather, precipitation, rainOpacity);
    }
    public ReplayEnvironmentOptions withRecordedTime() {
        return new ReplayEnvironmentOptions(false, timeTicks, weather, precipitation, rainOpacity);
    }
    public ReplayEnvironmentOptions withWeather(Weather value) {
        return new ReplayEnvironmentOptions(fixedTime, timeTicks, value, precipitation, rainOpacity);
    }
    public ReplayEnvironmentOptions withPrecipitation(float value) {
        return new ReplayEnvironmentOptions(fixedTime, timeTicks, weather, value, rainOpacity);
    }
    public ReplayEnvironmentOptions withRainOpacity(float value) {
        return new ReplayEnvironmentOptions(fixedTime, timeTicks, weather, precipitation, value);
    }
    public long visibleTime(long recordedTime) {
        // Retain the recorded day/moon phase and change only the sun's position.
        return fixedTime ? recordedTime - Math.floorMod(recordedTime, DAY_LENGTH) + timeTicks
                : recordedTime;
    }
    public float rainGradient(float recordedGradient) {
        return switch (weather) {
            case RECORDED -> recordedGradient;
            case CLEAR -> 0F;
            case RAIN, THUNDER -> precipitation;
        };
    }
    public float thunderGradient(float recordedGradient) {
        return switch (weather) {
            case RECORDED -> recordedGradient;
            case CLEAR, RAIN -> 0F;
            case THUNDER -> precipitation;
        };
    }
    public boolean raining() {
        return (weather == Weather.RAIN || weather == Weather.THUNDER) && precipitation > 0F;
    }
    public boolean thundering() {
        return weather == Weather.THUNDER && precipitation > 0F;
    }
    public static String clockTime(long ticks) {
        int minutes = (int) (Math.floorMod(ticks, DAY_LENGTH) * 1440 / DAY_LENGTH);
        minutes = (minutes + 360) % 1440; // Tick 0 is 06:00.
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Accept 0..23999 ticks or HH:MM. Invalid input does not alter the selected time. */
    public static OptionalInt parseTime(String input) {
        if (input == null) return OptionalInt.empty();
        String value = input.trim();
        try {
            if (value.matches("[0-9]{1,5}")) {
                int ticks = Integer.parseInt(value);
                return ticks < DAY_LENGTH ? OptionalInt.of(ticks) : OptionalInt.empty();
            }
            if (value.matches("[0-9]{1,2}:[0-9]{2}")) {
                String[] parts = value.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                if (hours > 23 || minutes > 59) return OptionalInt.empty();
                int sinceDawn = Math.floorMod(hours * 60 + minutes - 360, 1440);
                return OptionalInt.of((sinceDawn * DAY_LENGTH + 1439) / 1440);
            }
        } catch (NumberFormatException ignored) {}
        return OptionalInt.empty();
    }

    public void write(Writer writer) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("fixedTime", Boolean.toString(fixedTime));
        properties.setProperty("timeTicks", Integer.toString(timeTicks));
        properties.setProperty("weather", weather.name());
        properties.setProperty("precipitation", Float.toString(precipitation));
        // Additive v1 field: older recordings retain all time/weather settings.
        properties.setProperty("rainOpacity", Float.toString(rainOpacity));
        properties.store(writer, "xPiZzAxAnimation visual time and weather");
    }
    public static ReplayEnvironmentOptions read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        try {
            if (!"1".equals(properties.getProperty("version"))) return RECORDED;
            return new ReplayEnvironmentOptions(
                    Boolean.parseBoolean(properties.getProperty("fixedTime", "false")),
                    Integer.parseInt(properties.getProperty("timeTicks", "6000")),
                    Weather.valueOf(properties.getProperty("weather", "RECORDED")),
                    Float.parseFloat(properties.getProperty("precipitation", "1")),
                    readRainOpacity(properties));
        } catch (IllegalArgumentException ignored) {
            return RECORDED;
        }
    }

    private static float readRainOpacity(Properties properties) {
        try {
            return Float.parseFloat(properties.getProperty("rainOpacity",
                    Float.toString(DEFAULT_RAIN_OPACITY)));
        } catch (NumberFormatException ignored) {
            return DEFAULT_RAIN_OPACITY;
        }
    }
}
