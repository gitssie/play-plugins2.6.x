package play.api.config

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigMergeable, ConfigResolveOptions, ConfigValue}

class DynamicConfig (provider:ConfigProvider,globalConfig: Config) extends Config {

  @volatile var underlying:Config = provider.get.withFallback(globalConfig)

  provider.bind((_,change) => underlying = change.withFallback(globalConfig))

  override def getIntList(s: String) = underlying.getIntList(s)

  override def getLongList(s: String) = underlying.getLongList(s)

  override def resolve() = underlying.resolve()

  override def resolve(configResolveOptions: ConfigResolveOptions) = underlying.resolve(configResolveOptions)

  override def getEnum[T <: Enum[T]](aClass: Class[T], s: String) = underlying.getEnum(aClass,s)

  override def getMemorySize(s: String) = underlying.getMemorySize(s)

  override def getBytes(s: String) = underlying.getBytes(s)

  override def getMilliseconds(s: String) = underlying.getMilliseconds(s)

  override def withValue(s: String, configValue: ConfigValue) = underlying.withValue(s,configValue)

  override def getDuration(s: String, timeUnit: TimeUnit) = underlying.getDuration(s,timeUnit)

  override def getDuration(s: String) = underlying.getDuration(s)

  override def getList(s: String) = underlying.getList(s)

  override def isResolved = underlying.isResolved

  override def getAnyRef(s: String) = underlying.getAnyRef(s)

  override def getObjectList(s: String) = underlying.getObjectList(s)

  override def atKey(s: String) = underlying.atKey(s)

  override def getNumberList(s: String) = underlying.getNumberList(s)

  override def getObject(s: String) = underlying.getObject(s)

  override def entrySet() = underlying.entrySet()

  override def getDoubleList(s: String) = underlying.getDoubleList(s)

  override def withOnlyPath(s: String) = underlying.withOnlyPath(s)

  override def getInt(s: String) = underlying.getInt(s)

  override def getMemorySizeList(s: String) = underlying.getMemorySizeList(s)

  override def getNanosecondsList(s: String) = underlying.getNanosecondsList(s)

  override def resolveWith(config: Config) = underlying.resolveWith(config)

  override def resolveWith(config: Config, configResolveOptions: ConfigResolveOptions) = underlying.resolveWith(config,configResolveOptions)

  override def getConfigList(s: String) = underlying.getConfigList(s)

  override def hasPathOrNull(s: String) = underlying.hasPathOrNull(s)

  override def getStringList(s: String) = underlying.getStringList(s)

  override def getBytesList(s: String) = underlying.getBytesList(s)

  override def getBooleanList(s: String) = underlying.getBooleanList(s)

  override def checkValid(config: Config, strings: String*) = underlying.checkValid(config,strings :_*)

  override def getMillisecondsList(s: String) = underlying.getMillisecondsList(s)

  override def origin() = underlying.origin()

  override def getDouble(s: String) = underlying.getDouble(s)

  override def getNumber(s: String) = underlying.getNumber(s)

  override def root() = underlying.root()

  override def hasPath(s: String) = underlying.hasPath(s)

  override def getDurationList(s: String, timeUnit: TimeUnit) = underlying.getDurationList(s,timeUnit)

  override def getDurationList(s: String) = underlying.getDurationList(s)

  override def getBoolean(s: String) = underlying.getBoolean(s)

  override def getEnumList[T <: Enum[T]](aClass: Class[T], s: String) = underlying.getEnumList(aClass,s)

  override def withoutPath(s: String) = underlying.withoutPath(s)

  override def withFallback(configMergeable: ConfigMergeable) = underlying.withFallback(configMergeable)

  override def isEmpty = underlying.isEmpty

  override def getString(s: String) = underlying.getString(s)

  override def getConfig(s: String) = underlying.getConfig(s)

  override def getLong(s: String) = underlying.getLong(s)

  override def getValue(s: String) = underlying.getValue(s)

  override def getNanoseconds(s: String) = underlying.getNanoseconds(s)

  override def atPath(s: String) = underlying.atPath(s)

  override def getIsNull(s: String) = underlying.getIsNull(s)

  override def getAnyRefList(s: String) = underlying.getAnyRefList(s)

  override def toString: String = underlying.toString
}