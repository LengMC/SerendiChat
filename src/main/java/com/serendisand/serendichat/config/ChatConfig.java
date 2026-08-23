package com.serendisand.serendichat.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChatConfig {

    public String format = "[{stars}※] <{prefix}{nickname}{suffix}> -> {message}";
    public boolean adminColor = true;
    public int rainbowThreshold = 120;
    public int maxStars = 1000;
    public int starsPerHour = 5;

    public boolean markdownEnabled = true;
    public boolean emojiEnabled = true;
    public boolean itemDisplayEnabled = true;
    public Map<String, String> emojis = new LinkedHashMap<>();

    public String starsColor0 = "GRAY";
    public String starsColor20 = "GREEN";
    public String starsColor40 = "GOLD";
    public String starsColor60 = "AQUA";
    public String starsColor80 = "BLUE";
    public String starsColor100 = "RED";
    public String starsColorRainbow = "RAINBOW";
}
