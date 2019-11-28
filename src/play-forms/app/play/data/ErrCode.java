package play.data;

public class ErrCode {
    public static final ErrCode OK = new ErrCode("OK","OK");
    public static final ErrCode NOT_FOUND = new ErrCode("NOT_FOUND","ENTITY_NOT_FOUND");

    public final String code; 
    public final String message;
    
    public ErrCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public ErrCode to(String message){
        return new ErrCode(this.code,message);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ErrCode [code=" + code + ", message=" + message + "]";
    }
    
    @Override
    public boolean equals(Object o){
        if(o == null) return false;
        if(o instanceof String){
            String oo = (String) o;
            return code.equalsIgnoreCase(oo);
        }else if(o instanceof ErrCode){
            ErrCode co = (ErrCode) o;
            return code.equalsIgnoreCase(co.code);
        }else{
            return super.equals(o);
        }
    }
}

