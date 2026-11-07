package ru.wilyfox.trajectory.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** English and Russian carry the same keys and placeholders, and every setting has its texts. */
class TranslationParityTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd%]");

  private static Map<String, String> load(String language) throws Exception {
    var stream =
        TranslationParityTest.class.getResourceAsStream(
            "/assets/trajectory_projected/lang/" + language + ".json");
    assertNotNull(stream, language);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
      Map<String, String> out = new TreeMap<>();
      json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
      return out;
    }
  }

  private static List<String> placeholders(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = PLACEHOLDER.matcher(text);
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }

  @Test
  void sameKeysAndPlaceholders() throws Exception {
    Map<String, String> en = load("en_us");
    Map<String, String> ru = load("ru_ru");
    assertEquals(en.keySet(), ru.keySet());
    for (String key : en.keySet()) {
      assertEquals(placeholders(en.get(key)), placeholders(ru.get(key)), key);
      assertTrue(!ru.get(key).isBlank(), key);
    }
  }

  @Test
  void everySettingAndPageHasTexts() throws Exception {
    Map<String, String> en = load("en_us");
    for (var option : SettingsCatalog.create()) {
      String base = SettingsCatalog.PREFIX + "setting." + option.id();
      assertTrue(en.containsKey(base), base);
      assertTrue(en.containsKey(base + ".description"), base + ".description");
    }
    for (var page : SettingsCatalog.Page.values()) {
      String key =
          SettingsCatalog.PREFIX + "page." + page.name().toLowerCase(java.util.Locale.ROOT);
      assertTrue(en.containsKey(key), key);
    }
    for (var kind : SettingsCatalog.Kind.values()) {
      String key =
          SettingsCatalog.PREFIX + "hint." + kind.name().toLowerCase(java.util.Locale.ROOT);
      assertTrue(en.containsKey(key), key);
    }
  }
}
