package play.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

public class PageForm {
    @Constraints.Required
    @Constraints.Min(0)
    private Integer page = 0;

    @Constraints.Min(1)
    @Constraints.Max(1000)
    private Integer size = 10;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    @JsonIgnore
    public int getFirstRow(){
        return page * size;
    }

    @JsonIgnore
    public int getMaxRow(){
        return size;
    }
}
