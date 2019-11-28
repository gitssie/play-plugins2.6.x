package play.data;

import io.ebean.PagedList;

import java.io.Serializable;
import java.util.List;

public class Paging<A> implements Serializable {
    private Integer curPage;
    private Integer pageSize;
    private Integer pageCount;
    private Integer totalRows;
    private List<A> innerData;

    public Paging(){}

    public Paging(PagedList<A> page){
        this.curPage = page.getPageIndex();
        this.pageSize = page.getPageSize();
        this.pageCount = page.getTotalPageCount();
        this.totalRows = page.getTotalCount();
        this.innerData = page.getList();
    }

    public Integer getCurPage() {
        return curPage;
    }

    public void setCurPage(Integer curPage) {
        this.curPage = curPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public List<A> getInnerData() {
        return innerData;
    }

    public void setInnerData(List<A> innerData) {
        this.innerData = innerData;
    }
}
