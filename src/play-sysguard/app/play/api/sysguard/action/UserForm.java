package play.api.sysguard.action;

import play.data.validation.Constraints;

public class UserForm {
    @Constraints.Required
    protected String email;
    @Constraints.Required
    protected String password;
    protected String redirect;

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
