package com.github.test;

import play.data.validation.Constraints;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class TestForm {

    @Constraints.Required
    @Constraints.MaxLength(3)
    private String hello;
    @Constraints.Required
    @Constraints.Max(10)
    @Constraints.Min(0)
    private int id;

    @Constraints.Required
    @Valid
    private InnerForm innerForm;

    public String getHello() {
        return hello;
    }

    public void setHello(String hello) {
        this.hello = hello;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public static class InnerForm{
        @Constraints.Required
        private String hello;

        public String getHello() {
            return hello;
        }

        public void setHello(String hello) {
            this.hello = hello;
        }
    }

    public InnerForm getInnerForm() {
        return innerForm;
    }

    public void setInnerForm(InnerForm innerForm) {
        this.innerForm = innerForm;
    }
}
