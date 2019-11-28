package play.api.httpproxy

import javax.inject.{Inject, Singleton}

import akka.dispatch.Futures
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.http.Writeable._
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, InjectedController}

import scala.util.{Failure, Success, Try}

@Singleton
class EmailHttpProxy@Inject() (mailerClient: MailerClient,conf:Configuration) extends InjectedController with play.api.i18n.I18nSupport{
  private val logger = LoggerFactory.getLogger(classOf[EmailHttpProxy])

  case class EmailForm(val subject:String,val toEmail:String, val body:String)
  val emailForm = Form(
    mapping(
      "subject" -> text,
      "toEmail" -> text,
      "body" -> text,
    )(EmailForm.apply)(EmailForm.unapply)
  )

  def doSendEmail(form :EmailForm) = {
    val email = Email(
      form.subject,
      conf.get[String]("play.mailer.user"),
      Seq(form.toEmail),
      bodyHtml = Some(form.body)
    )
    mailerClient.send(email)
  }

  def sendMail(): Action[AnyContent] = Action.async{ implicit req =>
    val dForm = emailForm.bindFromRequest()
    if(dForm.hasErrors){
      logger.warn("send mail request params lack:{}",dForm.errorsAsJson)
      Futures.successful(Ok(dForm.errorsAsJson))
    }else{
      Try(doSendEmail(dForm.get)) match {
        case Success(r) => Futures.successful(Ok("success"))
        case Failure(e) => {
          logger.error("send mail fail",e)
          Futures.successful(Ok("fail"))
        }
      }
    }
  }
}
