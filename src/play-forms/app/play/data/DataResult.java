package play.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public interface DataResult extends Serializable{

    @JsonIgnore
    boolean isSuccess();

    @JsonIgnore
    boolean isFailure();

    @JsonIgnore
    ErrCode getErrCode();
}
