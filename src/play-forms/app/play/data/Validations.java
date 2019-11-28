package play.data;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class Validations {

    public static <T> void required(Form<T> form, String... keys){
        for (String key : keys) {
            String value = form.data().get(key);
            if(StringUtils.isEmpty(StringUtils.trim(value))){
                form.reject(key,"error.required", Lists.newArrayListWithCapacity(0));
            }
        }
    }

    public static <T> void filter(Form<T> form, String... keys) {
        for(String key : keys){
            form.data().remove(key);
        }
    }

    public static <T> void reject(Form<T> form, String key, String errmsg){
        List<ValidationError> error = new ArrayList<ValidationError>(4);
        error.add(new ValidationError(key,errmsg));
        form.errors().put(key,error);
    }
    
    /*
    public static <T> void required(String key, Form<T> form, JsonNode data) {
        ObjectNode onode = (ObjectNode) data;
        JsonNode keyValue = onode.get(key);
        String value = keyValue == null ? null : keyValue.asText();
        if(StringUtils.isEmpty(StringUtils.trim(value))){
            List<ValidationError> error = new ArrayList<ValidationError>(2);
            error.add(new ValidationError(key,"error.required"));
            form.errors().put(key,error);
        }
    }*/
}