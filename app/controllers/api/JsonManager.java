package controllers.api;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper methods for writing REST API routines
 * @author mattwigway
 *
 */
public class JsonManager<T> {
    private static ObjectMapper om = new ObjectMapper();
    
    private Class<T> theClass;
    
    /**
     * Convert an object to its JSON representation
     * @param o the object to convert
     * @return the JSON string
     * @throws JsonProcessingException 
     */
    public String write (T o) throws JsonProcessingException {
        return om.writeValueAsString(o);
    }
    
    public T read (String s) throws JsonParseException, JsonMappingException, IOException {
        return om.readValue(s, theClass);
    }
    
    public T read (JsonParser p) throws JsonParseException, JsonMappingException, IOException {
        return om.readValue(p, theClass);
    }
}
