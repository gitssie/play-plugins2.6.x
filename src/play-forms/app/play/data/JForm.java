/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.Errors;
import play.data.format.Formatters;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import play.i18n.MessagesApi;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.HttpVerbs;

import javax.validation.ConstraintViolation;
import javax.validation.metadata.ConstraintDescriptor;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static play.libs.F.Tuple;

/**
 * Helper to manage HTML form description, submission and validation.
 */
public class JForm<T> extends Form<T>{

    /** Statically compiled Pattern for replacing ".<collection element>" to get the field from a violation.  */
    private static final Pattern REPLACE_COLLECTION_ELEMENT = Pattern.compile(".<collection element>", Pattern.LITERAL);

    /** Statically compiled Pattern for replacing "typeMismatch" in Form errors. */
    private static final Pattern REPLACE_TYPEMISMATCH = Pattern.compile("typeMismatch", Pattern.LITERAL);

    // --

    private final String rootName;
    private final Class<T> backedType;
    private final Map<String,String> data;
    private final List<ValidationError> errors;
    private final Optional<T> value;
    private final Class<?>[] groups;
    final MessagesApi messagesApi;
    final Formatters formatters;
    final javax.validation.Validator validator;

    /**
     * Creates a new <code>Form</code>.  Consider using a {@link FormFactory} rather than this constructor.
     *
     * @param clazz wrapped class
     * @param messagesApi    messagesApi component.
     * @param formatters     formatters component.
     * @param validator      validator component.
     */
    public JForm(Class<T> clazz, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(null, clazz, messagesApi, formatters, validator);
    }

    public JForm(String rootName, Class<T> clazz, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, (Class<?>)null, messagesApi, formatters, validator);
    }

    public JForm(String rootName, Class<T> clazz, Class<?> group, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, group != null ? new Class[]{group} : null, messagesApi, formatters, validator);
    }

    public JForm(String rootName, Class<T> clazz, Class<?>[] groups, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, new HashMap<>(), new ArrayList<>(), Optional.empty(), groups, messagesApi, formatters, validator);
    }

    public JForm(String rootName, Class<T> clazz, Map<String,String> data, List<ValidationError> errors, Optional<T> value, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, data, errors, value, (Class<?>)null, messagesApi, formatters, validator);
    }

    public JForm(String rootName, Class<T> clazz, Map<String,String> data, List<ValidationError> errors, Optional<T> value, Class<?> group, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, data, errors, value, group != null ? new Class[]{group} : null, messagesApi, formatters, validator);
    }

    /**
     * Creates a new <code>Form</code>.  Consider using a {@link FormFactory} rather than this constructor.
     *
     * @param rootName    the root name.
     * @param clazz wrapped class
     * @param data the current form data (used to display the form)
     * @param errors the collection of errors associated with this form
     * @param value optional concrete value of type <code>T</code> if the form submission was successful
     * @param groups    the array of classes with the groups.
     * @param messagesApi needed to look up various messages
     * @param formatters used for parsing and printing form fields
     * @param validator the validator component.
     */
    public JForm(String rootName, Class<T> clazz, Map<String,String> data, List<ValidationError> errors, Optional<T> value, Class<?>[] groups, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        super(rootName,clazz,data,errors,value,groups,messagesApi,formatters,validator);
        this.rootName = rootName;
        this.backedType = clazz;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.value = value;
        this.groups = groups;
        this.messagesApi = messagesApi;
        this.formatters = formatters;
        this.validator = validator;
    }

    protected JsonNode requestJsonData(Http.Request request) {
        ObjectNode data = Json.newObject();

        if (request.body().asFormUrlEncoded() != null) {
            Map<String,String[]> urlFormEncoded = request.body().asFormUrlEncoded();
            fillDataWith(data,urlFormEncoded);
        }

        if (request.body().asMultipartFormData() != null) {
            Map<String,String[]> multipartFormData = request.body().asMultipartFormData().asFormUrlEncoded();
            fillDataWith(data,multipartFormData);
        }

        ObjectNode jsonData = Json.newObject();
        if (request.body().asJson() != null) {
            jsonData = (ObjectNode)request.body().asJson();
        }

        fillDataWith(data,jsonData);

        if(!request.method().equalsIgnoreCase(HttpVerbs.POST) && !request.method().equalsIgnoreCase(HttpVerbs.PUT) && !request.method().equalsIgnoreCase(HttpVerbs.PATCH)) {
            fillDataWith(data,request.queryString());
        }

        return data;
    }

    private void fillDataWith(ObjectNode data, Map<String, String[]> urlFormEncoded) {
        urlFormEncoded.forEach((key, values) -> {
            if (key.endsWith("[]")) {
                String k = key.substring(0, key.length() - 2);
                for (int i = 0; i < values.length; i++) {
                    data.put(k + "[" + i + "]", StringUtils.trim(values[i]));
                }
            } else if (values.length > 0) {
                data.put(key, StringUtils.trim(values[0]));
            }
        });
    }

    private void fillDataWith(ObjectNode data, ObjectNode jsonData) {
        Iterator<Map.Entry<String, JsonNode>> i = jsonData.fields();
        while(i.hasNext()){
            Map.Entry<String, JsonNode> node = i.next();
            if(node.getValue() != null && node.getValue() instanceof TextNode){
                data.set(node.getKey(),TextNode.valueOf(StringUtils.trim(node.getValue().asText())));
            }else{
                data.set(node.getKey(),node.getValue());
            }
        }
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @param allowedFields    the fields that should be bound to the form, all fields if not specified.
     * @return a copy of this form filled with the new data
     */
    public JForm<T> bindFromRequest(String... allowedFields) {
        return bind(requestJsonData(play.mvc.Controller.request()), allowedFields);
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @param request          the request to bind data from.
     * @param allowedFields    the fields that should be bound to the form, all fields if not specified.
     * @return a copy of this form filled with the new data
     */
    public JForm<T> bindFromRequest(Http.Request request, String... allowedFields) {
        return bind(requestJsonData(request), allowedFields);
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @param requestData      the map of data to bind from
     * @param allowedFields    the fields that should be bound to the form, all fields if not specified.
     * @return a copy of this form filled with the new data
     */
    public JForm<T> bindFromRequest(Map<String,String[]> requestData, String... allowedFields) {
        ObjectNode data = Json.newObject();
        fillDataWith(data, requestData);
        return bind(data, allowedFields);
    }

    private static final Set<String> internalAnnotationAttributes = new HashSet<>(3);
    static {
        internalAnnotationAttributes.add("message");
        internalAnnotationAttributes.add("groups");
        internalAnnotationAttributes.add("payload");
    }

    protected List<Object> getArgumentsListForConstraint(String objectName, String field, ConstraintDescriptor<?> descriptor) {
        List<Object> arguments = new LinkedList<>();
        String[] codes = new String[] {objectName + Errors.NESTED_PATH_SEPARATOR + field, field};
        //arguments.add(new DefaultMessageSourceResolvable(codes, field));
        // Using a TreeMap for alphabetical ordering of attribute names
        Map<String, Object> attributesToExpose = new TreeMap<>();
        descriptor.getAttributes().forEach((attributeName, attributeValue) -> {
            if (!internalAnnotationAttributes.contains(attributeName)) {
                attributesToExpose.put(attributeName, attributeValue);
            }
        });
        arguments.addAll(attributesToExpose.values());
        return arguments;
    }

    /**
     * When dealing with @ValidateWith annotations, and message parameter is not used in
     * the annotation, extract the message from validator's getErrorMessageKey() method
     *
     * @param violation the constraint violation.
     * @return the message associated with the constraint violation.
     */
    protected String getMessageForConstraintViolation(ConstraintViolation<Object> violation) {
        String errorMessage = violation.getMessage();
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
        if (annotation instanceof Constraints.ValidateWith) {
            Constraints.ValidateWith validateWithAnnotation = (Constraints.ValidateWith)annotation;
            if (violation.getMessage().equals(Constraints.ValidateWithValidator.defaultMessage)) {
                Constraints.ValidateWithValidator validateWithValidator = new Constraints.ValidateWithValidator();
                validateWithValidator.initialize(validateWithAnnotation);
                Tuple<String, Object[]> errorMessageKey = validateWithValidator.getErrorMessageKey();
                if (errorMessageKey != null && errorMessageKey._1 != null) {
                    errorMessage = errorMessageKey._1;
                }
            }
        }

        return errorMessage;
    }


    private Set<ConstraintViolation<Object>> runValidation(T dataBinder) {
        return withRequestLocale(() -> {
            if (groups != null) {
                return validator.validate(dataBinder, groups);
            } else {
                return validator.validate(dataBinder);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void addConstraintViolationToBindingResult(List<ValidationError> builder,ConstraintViolation<Object> violation) {
        String field = REPLACE_COLLECTION_ELEMENT.matcher(violation.getPropertyPath().toString()).replaceAll("");
        if (field != null) {
            try {
                final Object dynamicPayload = violation.unwrap(HibernateConstraintViolation.class).getDynamicPayload(Object.class);

                if (dynamicPayload instanceof String) {
                    builder.add(new ValidationError("",(String)dynamicPayload));
                } else if (dynamicPayload instanceof ValidationError) {
                    final ValidationError error = (ValidationError) dynamicPayload;
                    builder.add(error);
                } else if (dynamicPayload instanceof List) {
                   builder.addAll((List<ValidationError>) dynamicPayload);
                } else {
                    builder.add(new ValidationError(field,getMessageForConstraintViolation(violation),getArgumentsListForConstraint(rootName, field, violation.getConstraintDescriptor())));
                }
            } catch (NotReadablePropertyException ex) {
                throw new IllegalStateException("JSR-303 validated property '" + field +
                        "' does not have a corresponding accessor for data binding - " +
                        "check your DataBinder's configuration (bean property versus direct field access)", ex);
            }
        }
    }

    protected T getResult(JsonNode data){
        return Json.fromJson(data,backedType);
    }

    /**
     * Binds data to this form - that is, handles form submission.
     *
     * @param result data to submit
     * @param allowedFields    the fields that should be bound to the form, all fields if not specified.
     * @return a copy of this form filled with the new data
     */
    public JForm<T> validate(T result, String... allowedFields) {
        final List<ValidationError> errors = Lists.newArrayList();
        final Set<ConstraintViolation<Object>> validationErrors = runValidation(result);

        boolean hasAnyError = validationErrors.size() > 0;

        if (hasAnyError) {
            validationErrors.forEach(violation -> addConstraintViolationToBindingResult(errors,violation));
            return new JForm<T>(rootName, backedType, Maps.newHashMap(), errors, Optional.ofNullable(result), groups, messagesApi, formatters, this.validator);
        }
        return new JForm<T>(rootName, backedType,  Maps.newHashMap(), errors, Optional.ofNullable(result), groups, messagesApi, formatters, this.validator);
    }

    /**
     * Binds data to this form - that is, handles form submission.
     *
     * @param data data to submit
     * @param allowedFields    the fields that should be bound to the form, all fields if not specified.
     * @return a copy of this form filled with the new data
     */
    public JForm<T> bind(JsonNode data, String... allowedFields) {
        T result = null;
        final List<ValidationError> errors = Lists.newArrayList();
        try{
            result = getResult(data);
            return validate(result,allowedFields);
        }catch (RuntimeException e){
            if(e.getCause() != null && e.getCause() instanceof InvalidFormatException){
                InvalidFormatException ex = (InvalidFormatException) e.getCause();
                String msg = "error.invalid";
                if(ex.getTargetType().isAssignableFrom(Short.class) || ex.getTargetType().isAssignableFrom(Integer.class) || ex.getTargetType().isAssignableFrom(Long.class)){
                    msg = "error.number";
                }else if(ex.getTargetType().isAssignableFrom(Number.class)){
                    msg = "error.real";
                }else if(ex.getTargetType().isAssignableFrom(Date.class)){
                    msg = "error.invalid.java.util.Date";
                }
                errors.add(new ValidationError(ex.getPath().get(0).getFieldName(),msg));
            }else {
                throw e;
            }
        }
        /**
        final Object globalError = callLegacyValidateMethod(result);
        if (globalError != null) {
            final List<ValidationError> errors = new ArrayList<>();
            if (globalError instanceof String) {
                errors.add(new ValidationError("", (String)globalError, new ArrayList<>()));
            } else if (globalError instanceof List) {
                errors.addAll((List<ValidationError>) globalError);
            } else if (globalError instanceof Map) {
                ((Map<String,List<ValidationError>>)globalError).forEach((key, values) -> errors.addAll(values));
            }
            return new JForm<T>(rootName, backedType,  Maps.newHashMap(), errors, Optional.ofNullable(result), groups, messagesApi, formatters, this.validator);
        }**/

        return new JForm<T>(rootName, backedType,  Maps.newHashMap(), errors, Optional.ofNullable(result), groups, messagesApi, formatters, this.validator);
    }


    /**
     * @return the actual form data.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #rawData()} instead which returns an unmodifiable map.
     */
    @Deprecated
    public Map<String,String> data() {
        return data;
    }

    /**
     * @return the actual form data as unmodifiable map.
     */
    public Map<String,String> rawData() {
        return Collections.unmodifiableMap(data);
    }

    public String name() {
        return rootName;
    }

    /**
     * @return the actual form value - even when the form contains validation errors.
     */
    public Optional<T> value() {
        return value;
    }

    /**
     * Populates this form with an existing value, used for edit forms.
     *
     * @param value existing value of type <code>T</code> used to fill this form
     * @return a copy of this form filled with the new data
     */
    @Override
    public JForm<T> fill(T value) {
        if (value == null) {
            throw new RuntimeException("Cannot fill a form with a null value");
        }
        return validate(value);
    }

    /**
     * @return <code>true</code> if there are any errors related to this form.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * @return <code>true</code> if there any global errors related to this form.
     */
    public boolean hasGlobalErrors() {
        return !globalErrors().isEmpty();
    }

    /**
     * Retrieve all global errors - errors without a key.
     *
     * @return All global errors.
     */
    public List<ValidationError> globalErrors() {
        return Collections.unmodifiableList(errors.stream().filter(error -> error.key().isEmpty()).collect(Collectors.toList()));
    }

    /**
     * Retrieves the first global error (an error without any key), if it exists.
     *
     * @return An error or <code>null</code>.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #getGlobalError()} instead.
     */
    @Deprecated
    public ValidationError globalError() {
        return this.getGlobalError().orElse(null);
    }

    /**
     * Retrieves the first global error (an error without any key), if it exists.
     *
     * @return An error.
     */
    public Optional<ValidationError> getGlobalError() {
        return globalErrors().stream().findFirst();
    }

    /**
     * Returns all errors.
     *
     * @return All errors associated with this form.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #allErrors()} instead.
     */
    @Deprecated
    public Map<String,List<ValidationError>> errors() {
        return Collections.unmodifiableMap(this.errors.stream().collect(Collectors.groupingBy(error -> error.key())));
    }

    /**
     * Returns all errors.
     *
     * @return All errors associated with this form.
     */
    public List<ValidationError> allErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * @param key    the field name associated with the error.
     * @return All errors for this key.
     */
    public List<ValidationError> errors(String key) {
        if(key == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(errors.stream().filter(error -> error.key().equals(key)).collect(Collectors.toList()));
    }

    /**
     * @param key    the field name associated with the error.
     * @return an error by key, or null.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #getError(String)} instead.
     */
    @Deprecated
    public ValidationError error(String key) {
        return this.getError(key).orElse(null);
    }

    /**
     * @param key    the field name associated with the error.
     * @return an error by key
     */
    public Optional<ValidationError> getError(String key) {
        return errors(key).stream().findFirst();
    }

    /**
     * @return the form errors serialized as Json.
     */
    public com.fasterxml.jackson.databind.JsonNode errorsAsJson() {
        return errorsAsJson(Http.Context.current() != null ? Http.Context.current().lang() : null);
    }

    /**
     * Returns the form errors serialized as Json using the given Lang.
     * @param lang    the language to use.
     * @return the JSON node containing the errors.
     */
    public com.fasterxml.jackson.databind.JsonNode errorsAsJson(play.i18n.Lang lang) {
        Map<String, List<String>> allMessages = new HashMap<>();
        errors.forEach(error -> {
            if (error != null) {
                final List<String> messages = new ArrayList<>();
                if (messagesApi != null && lang != null) {
                    final List<String> reversedMessages = new ArrayList<>(error.messages());
                    Collections.reverse(reversedMessages);
                    messages.add(messagesApi.get(lang, reversedMessages, translateMsgArg(error.arguments(), messagesApi, lang)));
                } else {
                    messages.add(error.message());
                }
                allMessages.put(error.key(), messages);
            }
        });
        return play.libs.Json.toJson(allMessages);
    }

    private Object translateMsgArg(List<Object> arguments, MessagesApi messagesApi, play.i18n.Lang lang) {
        if (arguments != null) {
            return arguments.stream().map(arg -> {
                    if (arg instanceof String) {
                        return messagesApi != null ? messagesApi.get(lang, (String)arg) : (String)arg;
                    }
                    if (arg instanceof List) {
                        return ((List<?>) arg).stream().map(key -> messagesApi != null ? messagesApi.get(lang, (String)key) : (String)key).collect(Collectors.toList());
                    }
                    return arg;
                }).collect(Collectors.toList());
        } else {
            return null;
       }
    }

    /**
     * Gets the concrete value only if the submission was a success.
     * If the form is invalid because of validation errors this method will throw an exception.
     * If you want to retrieve the value even when the form is invalid use {@link #value()} instead.
     *
     * @throws IllegalStateException if there are errors binding the form, including the errors as JSON in the message
     * @return the concrete value.
     */
    public T get() {
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Error(s) binding form: " + errorsAsJson());
        }
        return value.get();
    }

    /**
     * Adds an error to this form.
     *
     * @param error the <code>ValidationError</code> to add.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #withError(ValidationError)} instead.
     */
    @Deprecated
    public void reject(ValidationError error) {
        if (error == null) {
            throw new NullPointerException("Can't reject null-values");
        }
        errors.add(error);
    }

    /**
     * @param error the <code>ValidationError</code> to add to the returned form.
     *
     * @return a copy of this form with the given error added.
     */
    public JForm<T> withError(final ValidationError error) {
        if (error == null) {
            throw new NullPointerException("Can't reject null-values");
        }
        final List<ValidationError> copiedErrors = new ArrayList<>(this.errors);
        copiedErrors.add(error);
        return new JForm<T>(this.rootName, this.backedType, this.data, copiedErrors, this.value, this.groups, this.messagesApi, this.formatters, this.validator);
    }

    /**
     * Adds an error to this form.
     *
     * @param key the error key
     * @param error the error message
     * @param args the error arguments
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #withError(String, String, List)} instead.
     */
    @Deprecated
    public void reject(String key, String error, List<Object> args) {
        reject(new ValidationError(key, error, args));
    }

    /**
     * @param key the error key
     * @param error the error message
     * @param args the error arguments
     *
     * @return a copy of this form with the given error added.
     */
    public JForm<T> withError(final String key, final String error, final List<Object> args) {
        return withError(new ValidationError(key, error, args != null ? new ArrayList<>(args) : new ArrayList<>()));
    }

    /**
     * Adds an error to this form.
     *
     * @param key the error key
     * @param error the error message
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #withError(String, String)} instead.
     */
    @Deprecated
    public void reject(String key, String error) {
        reject(key, error, new ArrayList<>());
    }

    /**
     * @param key the error key
     * @param error the error message
     *
     * @return a copy of this form with the given error added.
     */
    public JForm<T> withError(final String key, final String error) {
        return withError(key, error, new ArrayList<>());
    }

    /**
     * Adds a global error to this form.
     *
     * @param error the error message
     * @param args the error arguments
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #withGlobalError(String, List)} instead.
     */
    @Deprecated
    public void reject(String error, List<Object> args) {
        reject(new ValidationError("", error, args));
    }

    /**
     * @param error the global error message
     * @param args the global error arguments
     *
     * @return a copy of this form with the given global error added.
     */
    public JForm<T> withGlobalError(final String error, final List<Object> args) {
        return withError("", error, args);
    }

    /**
     * Adds a global error to this form.
     *
     * @param error the error message.
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #withGlobalError(String)} instead.
     */
    @Deprecated
    public void reject(String error) {
        reject("", error, new ArrayList<>());
    }

    /**
     * @param error the global error message
     *
     * @return a copy of this form with the given global error added.
     */
    public JForm<T> withGlobalError(final String error) {
        return withGlobalError(error, new ArrayList<>());
    }

    /**
     * Discards errors of this form
     *
     * @deprecated Deprecated as of 2.6.0. Use {@link #discardingErrors()} instead.
     */
    @Deprecated
    public void discardErrors() {
        errors.clear();
    }

    /**
     * @return a copy of this form but with the errors discarded.
     */
    public JForm<T> discardingErrors() {
        return new JForm<T>(this.rootName, this.backedType, this.data, new ArrayList<>(), this.value, this.groups, this.messagesApi, this.formatters, this.validator);
    }

    public String toString() {
        return "Form(of=" + backedType + ", data=" + data + ", value=" + value +", errors=" + errors + ")";
    }


    /**
     * Sets the locale of the current request (if there is one) into Spring's LocaleContextHolder.
     *
     * @param <T> the return type.
     * @param code The code to execute while the locale is set
     * @return the result of the code block
     */
    public static <T> T withRequestLocale(Supplier<T> code) {
        try {
            LocaleContextHolder.setLocale(Http.Context.current().lang().toLocale());
        } catch(Exception e) {
            // Just continue (Maybe there is no context or some internal error in LocaleContextHolder). System default locale will be used.
        }
        try {
            return code.get();
        } finally {
            LocaleContextHolder.resetLocaleContext(); // Clean up ThreadLocal
        }
    }
}
