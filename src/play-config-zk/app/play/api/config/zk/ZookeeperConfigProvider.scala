package play.api.config.zk

import javax.inject.{Inject, Singleton}

import com.google.common.collect.Maps
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, PathChildrenCache, PathChildrenCacheEvent, PathChildrenCacheListener}
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode
import play.api.Configuration
import play.api.config.{ConfValue, ConfigProvider}
import play.api.inject.ApplicationLifecycle
import play.libs.Json

import scala.collection.JavaConverters._
import scala.concurrent.Future

@Singleton
class ZookeeperConfigProvider @Inject() (client:CuratorFramework,conf:Configuration,applicationLifecycle: ApplicationLifecycle) extends ConfigProvider{
  val basePath = conf.get[String]("play.config.zk.base-path")
  val configs = Maps.newHashMap[String,ConfValue]()

  var onChanged:(Option[String],Config) => Unit = _

  val nodeCache = new PathChildrenCache(client,basePath,true)

  override def bind(event: (Option[String],Config) => Unit): Unit = onChanged = event

  override def get: Config = {
    initialize
    toConfig
  }

  def initialize:Unit = {
    val st = client.checkExists().forPath(basePath)
    if(st == null) {
      client.create.creatingParentContainersIfNeeded().withMode(CreateMode.PERSISTENT).forPath(basePath)
    }
    nodeCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE)
    nodeCache.getListenable.addListener(new PathChildrenCacheListener {
      override def childEvent(client: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
        event.getType match {
          case PathChildrenCacheEvent.Type.CHILD_ADDED => fireOnUpdate(event.getData)
          case PathChildrenCacheEvent.Type.CHILD_UPDATED => fireOnUpdate(event.getData)
          case PathChildrenCacheEvent.Type.CHILD_REMOVED => fireOnRemove(event.getData)
          case _ =>
        }
      }
    })
    buildConfigs
  }

  def toConfig:Config = {
    val maps = Maps.newHashMap[String,String]()
    configs.asScala.foreach{
      case (k,v) => maps.put(k,v.value)
    }
    ConfigFactory.parseMap(maps)
  }

  def fireOnUpdate(child:ChildData):Unit = {
    val key = ZKPaths.getNodeFromPath((child.getPath))
    configs.put(key,getJsonValue(child))
    onChanged(Some(key),toConfig)
  }

  def fireOnRemove(child:ChildData):Unit = {
    configs.remove(ZKPaths.getNodeFromPath((child.getPath)))
    onChanged(None,toConfig)
  }

  def buildConfigs: Unit = {
    configs.clear()
    nodeCache.getCurrentData.asScala.foreach{child =>
        configs.put(ZKPaths.getNodeFromPath((child.getPath)),getJsonValue(child))
    }
  }

  applicationLifecycle.addStopHook(() => Future.successful(nodeCache.close))

  def getJsonValue(child:ChildData): ConfValue ={
    Json.fromJson(Json.parse(child.getData),classOf[ConfValue])
  }

  override def put(key: String, v: ConfValue): Unit = {
    val se = Json.stringify(Json.toJson(v))
    val path = ZKPaths.makePath(basePath,key)
    if(client.checkExists().forPath(path) != null){
      client.setData().forPath(path,se.getBytes())
    }else{
      client.create().withMode(CreateMode.PERSISTENT).forPath(path,se.getBytes())
    }
  }

  override def remove(key: String): Unit = {
    val path = ZKPaths.makePath(basePath,key)
    client.delete().forPath(path)
  }

  override def get(key: String) = configs.get(key)

  override def list = configs.values()

  override def checkExists(key: String) = get(key) != null
}