package play.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class ListResult<A> implements DataResult{
    private int status;
    private String code;
    private String message;
    private List<A> data;

    @JsonIgnore
    public boolean isSuccess(){
        return status == 200;
    }

    @JsonIgnore
    public boolean isFailure(){
        return status != 200;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<A> getData() {
        return data;
    }

    public void setData(List<A> data) {
        this.data = data;
    }

    @JsonIgnore
    public ErrCode getErrCode(){
        return new ErrCode(code,message);
    }
}
