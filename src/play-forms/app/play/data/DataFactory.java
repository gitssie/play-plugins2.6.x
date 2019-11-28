package play.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import play.data.format.Formatters;
import play.i18n.Lang;
import play.i18n.MessagesApi;
import play.libs.F;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Validator;
import java.util.Iterator;
import java.util.Map;

@Singleton
public class DataFactory extends FormFactory{
    private final Logger logger = LoggerFactory.getLogger(DataFactory.class);
    private final MessagesApi messagesApi;
    private final Formatters formatters;
    private final Validator validator;

    @Inject
    public DataFactory(MessagesApi messagesApi, Formatters formatters, Validator validator) {
        super(messagesApi, formatters, validator);
        this.messagesApi = messagesApi;
        this.formatters = formatters;
        this.validator = validator;
    }

    public Result ok(){
        ObjectNode onode = Json.newObject();
        onode.put("status",200);
        onode.put("traceId", MDC.get("traceId"));
        return Results.ok(onode);
    }

    public <A extends DataResult> Result ok(A data){
        return Results.ok(Json.toJson(data));
    }

    public <A> Result ok(A a){
        if(a != null && a instanceof DataResult){
            return Results.ok(Json.toJson(a));
        }
        ObjectNode onode = Json.newObject();
        onode.put("status",200);
        onode.put("traceId", MDC.get("traceId"));
        onode.putPOJO("data",a);
        return Results.ok(onode);
    }

    public Result bad(){
        ObjectNode onode = Json.newObject();
        onode.put("status",400);
        onode.put("traceId", MDC.get("traceId"));

        logResult(onode);

        return Results.ok(onode);
    }

    public Result bad(String msg){
        ObjectNode onode = Json.newObject();
        onode.put("status",400);
        onode.put("message",msg);
        onode.put("traceId", MDC.get("traceId"));

        logResult(onode);

        return Results.ok(onode);
    }

    public Result bad(String key,Object... args){
        String msg = this.messagesApi.get(Lang.defaultLang(),key,args);
        ObjectNode onode = Json.newObject();
        onode.put("status",400);
        onode.put("code",msg);
        onode.put("message",msg);
        onode.put("traceId", MDC.get("traceId"));

        logResult(onode);

        return Results.ok(onode);
    }

    public <A> Result bad(Form<A> dForm){
        StringBuilder buf = new StringBuilder();
        JsonNode errors = dForm.errorsAsJson();
        Iterator<Map.Entry<String, JsonNode>> iterator = errors.fields();
        while(iterator.hasNext()){
            Map.Entry<String, JsonNode> entry = iterator.next();
            buf.append(entry.getKey()).append(":").append(entry.getValue().get(0).asText("")).append(",");
        }
        if(buf.length() > 1) {
            buf.setLength(buf.length() - 1);
        }
        ObjectNode onode = Json.newObject();
        onode.put("status",400);
        onode.put("code","PARAM_ERROR");
        onode.put("message",buf.toString());
        onode.put("traceId", MDC.get("traceId"));

        logResult(onode);

        return Results.ok(onode);
    }

    public Result bad(ErrCode errCode){
        ObjectNode onode = Json.newObject();
        onode.put("status",400);
        onode.put("code",errCode.code);
        onode.put("message",errCode.message);
        onode.put("traceId", MDC.get("traceId"));

        logResult(onode);

        return Results.ok(onode);
    }

    public <A> Result ok(F.Either<A,ErrCode> e){
        if(e.right.isPresent()){
            return bad(e.right.get());
        }else{
            return ok(e.left.get());
        }
    }

    public <A> Result noContent(F.Either<A,ErrCode> e){
        if(e.right.isPresent()){
            return bad(e.right.get());
        }else{
            return ok();
        }
    }


    protected void logResult(ObjectNode onode){
        if(logger.isTraceEnabled()){
            logger.trace("response:{}",onode);
        }
    }

    /**
     * @return a dynamic form.
     */
    public DynamicForm form() {
        return new DynamicForm(messagesApi, formatters, validator);
    }

    /**
     * @param clazz    the class to map to a form.
     * @param <T>   the type of value in the form.
     * @return a new form that wraps the specified class.
     */
    public <T> Form<T> form(Class<T> clazz) {
        return new JForm<>(clazz, messagesApi, formatters, validator);
    }

    /**
     * @param <T>   the type of value in the form.
     * @param name the form's name.
     * @param clazz the class to map to a form.
     * @return a new form that wraps the specified class.
     */
    public <T> Form<T> form(String name, Class<T> clazz) {
        return new JForm<>(name, clazz, messagesApi, formatters, validator);
    }

    /**
     * @param <T>   the type of value in the form.
     * @param name the form's name
     * @param clazz the class to map to a form.
     * @param groups the classes of groups.
     * @return a new form that wraps the specified class.
     */
    public <T> Form<T> form(String name, Class<T> clazz, Class<?>... groups) {
        return new JForm<>(name, clazz, groups, messagesApi, formatters, validator);
    }

    /**
     * @param <T>   the type of value in the form.
     * @param clazz the class to map to a form.
     * @param groups the classes of groups.
     * @return a new form that wraps the specified class.
     */
    public <T> Form<T> form(Class<T> clazz, Class<?>... groups) {
        return new JForm<>(null, clazz, groups, messagesApi, formatters, validator);
    }


}
