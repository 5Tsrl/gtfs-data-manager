package utils;

public class StringUtils {
    /**
     * Clean a name to make it filesystem-friendly
     * @param name a name with any letters
     * @return a new name with weird letters removed/transliterated.
     */
    public static String getCleanName (String name) {
        return name.replace(' ', '_').replaceAll("[^A-Za-z0-9_-]", "");
    }
}
