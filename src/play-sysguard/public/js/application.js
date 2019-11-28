$(function () {

    $('#login-btn').click(function(){
        var params = {};
        $.each($('.login-form').serializeArray(),function(n,e){
            params[e.name] = e.value;
        });

        $.post("/login", params,function(data){
                if(data.status != 200) {
                    var h = $('#has-error');
                    var msg = data.message;
                    if(data.status == 400){
                        msg = '请您输入用户名、密码';
                    }else{
                        msg = '用户名密码不正确';
                    }
                    $('label',h).text(msg);
                    h.show();
                    setTimeout(function(){
                        $('#has-error').hide();
                    },1500);
                }else{
                    location.href = params['redirect'];
                }
            }, "json",function(){
        });
        return false;
    });
});