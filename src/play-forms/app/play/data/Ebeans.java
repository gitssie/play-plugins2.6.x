package play.data;

import com.google.common.collect.Sets;
import io.ebean.Ebean;
import io.ebean.EbeanServer;
import io.ebean.plugin.BeanType;
import io.ebean.plugin.Property;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class Ebeans {

    public static <K> Collection<K> getIds(List<?> objects){
        Set<K> ids = Sets.newHashSet();
        EbeanServer ebeanServer = Ebean.getDefaultServer();
        for(Object obj : objects){
            if(obj == null) continue;
            ids.add((K)ebeanServer.getBeanId(obj));
        }
        return ids;
    }

    public static <K> Collection<K> getFields(List<?> objects, String field){
        Set<K> ids = Sets.newHashSet();
        EbeanServer ebeanServer = Ebean.getDefaultServer();
        BeanType<?> beanType = null;
        Property property = null;
        for(Object obj : objects){
            if(obj == null) continue;
            if(beanType == null){
                beanType = ebeanServer.getPluginApi().getBeanType(obj.getClass());
                property = beanType.getProperty(field);
            }
            ids.add((K)property.getVal(obj));
        }
        return ids;
    }

    public static <K> boolean isModify(K k,Object form) throws Exception {
        EbeanServer ebeanServer = Ebean.getDefaultServer();
        BeanType<?> beanType = ebeanServer.getPluginApi().getBeanType(k.getClass());
        if(beanType == null){
            return false;
        }
        PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(form.getClass());
        Property property = null;
        for(PropertyDescriptor pd : pds){
            property = beanType.getProperty(pd.getName());//获取数据实体的属性
            if(property != null){
                if (isFieldModified(k, form, pd, property)) return true;
            }
        }
        return false;
    }

    public static <K> boolean isModify(K k,Object form,String... fields) throws Exception {
        EbeanServer ebeanServer = Ebean.getDefaultServer();
        BeanType<?> beanType = ebeanServer.getPluginApi().getBeanType(k.getClass());
        if(beanType == null){
            return false;
        }
        PropertyDescriptor pd;
        Property property = null;
        for(String fd : fields){
            pd = BeanUtils.getPropertyDescriptor(form.getClass(),fd);
            if(pd != null){
                property = beanType.getProperty(pd.getName());//获取数据实体的属性
                if (isFieldModified(k, form, pd, property)) return true;
            }
        }
        return false;
    }

    private static <K> boolean isFieldModified(K k, Object form, PropertyDescriptor pd, Property property) throws IllegalAccessException, InvocationTargetException {
        Object left;
        Object right;
        boolean fieldModify = false;
        if(property != null){
            left = property.getVal(k);
            right = pd.getReadMethod().invoke(form);
            if(left == null){
                fieldModify = right != null;
            }else if(right == null){
                fieldModify = left != null;
            }else{
                fieldModify = !left.equals(right);
            }
        }
        return fieldModify;
    }
}
