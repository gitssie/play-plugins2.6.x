package play.api.sysguard.service;

import com.typesafe.config.Config;
import play.Configuration;
import play.api.libs.changestream.actors.SqlActor;
import play.api.libs.changestream.actors.StateActor;
import play.api.libs.changestream.events.MutationWithInfo;
import scala.collection.immutable.ListMap;
import scala.collection.mutable.StringBuilder;
import scala.util.Success;

import java.math.BigDecimal;
import java.util.Set;

public class SyncDataActor extends SqlActor{
    private Configuration conf;

    public SyncDataActor(Config conf, StateActor sa) {
        super(conf, sa);
        this.conf = new Configuration(conf);
    }

    /**
     * 主要目的是过滤数据
     * 1、根据对应的表的配置项，表列所对应的值进行&&判断
     * @param m
     * @return
     */
    @Override
    public boolean matchRow(MutationWithInfo m){
        String tableName = m.mutation().tableName();
        ListMap<String, Object> data = (ListMap<String, Object>)m.message().get();
        Configuration tableConf = conf.getConfig("sync."+tableName);
        if(tableConf == null){
            return true;//不进行数据过滤
        }
        Set<String> columns = tableConf.keys();
        if(columns.size() == 0){
            return  true;//没有配置过滤项
        }
        boolean allow = false;
        for(String col : columns){
            allow = isAllowRow(tableConf,col,data);
            if(!allow){
                return false;
            }
        }
        return allow;
    }

    private boolean isAllowRow(Configuration tableConf, String col, ListMap<String, Object> data) {
        Object colVal = data.get(col).get();
        if(colVal != null){
            if(colVal instanceof Integer){
                return colVal.equals(tableConf.getInt(col,0));
            }else if(colVal instanceof Long){
                return colVal.equals(tableConf.getLong(col,0l));
            }else if(colVal instanceof BigDecimal){
                return colVal.equals(tableConf.getDouble(col,0d));
            }else if(colVal instanceof Boolean){
                return colVal.equals(tableConf.getBoolean(col,false));
            }else if(colVal instanceof String){
                return colVal.equals(tableConf.getString(col));
            }else{
                throw new IllegalArgumentException("illegal col:"+col+" value not supported");
            }
        }
        return false;
    }

    @Override
    public void processRow(StringBuilder buf,StateActor sa){
        System.out.println(buf.toString());

        sa.receive().apply(Success.apply(200)); //保存binlog位置信息
    }
}
