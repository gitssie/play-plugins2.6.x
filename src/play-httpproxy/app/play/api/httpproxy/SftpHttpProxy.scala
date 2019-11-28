package play.api.httpproxy

import javax.inject.{Inject, Singleton}

import akka.dispatch.Futures
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.io.IOUtils
import play.api.data.Forms._
import play.api.data._
import play.api.http.Writeable._
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, InjectedController, RangeResult}

@Singleton
class SftpHttpProxy @Inject() (fileCreator: TemporaryFileCreator) extends InjectedController with play.api.i18n.I18nSupport {

    case class SftpForm(val hostname:String,val username:String, val password:String, val source:String,val port:Option[Int])
    val sftpForm = Form(
      mapping(
        "hostname" -> text,
        "username" -> text,
        "password" -> text,
        "source" -> text,
        "port" -> optional(number)
      )(SftpForm.apply)(SftpForm.unapply)
    )

    def download(): Action[AnyContent] = Action.async{ implicit req =>
      val dForm = sftpForm.bindFromRequest()
      if(dForm.hasErrors){
        Futures.successful(Ok(dForm.errorsAsJson))
      }else{
        val data = dForm.get
        val ssh = new SSHClient
        try{
          val ssh = new SSHClient
          ssh.addHostKeyVerifier(new PromiscuousVerifier)
          ssh.connect(data.hostname,data.port.getOrElse(SSHClient.DEFAULT_PORT))
          ssh.authPassword(data.username, data.password)
          val tempFile = fileCreator.create().path.toFile
          val sftp = ssh.newSFTPClient
          sftp.get(data.source,tempFile.getAbsolutePath)
          Futures.successful(RangeResult.ofFile(tempFile,req.headers.get(RANGE),None))//发送文件
        }catch {
          case e:Throwable =>{
            val msg = Json.obj(
              "message" -> (e.getClass.getSimpleName + ":" + e.getMessage+",source:" + data.source))
            Futures.successful(Ok(msg))
          }
        }finally {
          IOUtils.closeQuietly(ssh)
        }
      }
    }
}
