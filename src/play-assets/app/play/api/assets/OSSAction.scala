package play.api.assets

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.zengularity.benji.exception.{BucketAlreadyExistsException, ObjectNotFoundException}
import com.zengularity.benji.{ObjectStorage => OOS}
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables._
import play.api.mvc.{BaseController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class OSSAction @Inject() (val controllerComponents: ControllerComponents,
                               oos: OOS)
                                (implicit ec: ExecutionContext, mat: Materializer) extends BaseController {

  def listBuckets = Action.async {
    oos.buckets.collect[List]().map { buckets =>
      Ok(Json.toJson(buckets.map(_.name)))
    }
  }

  def createBucket(bucketName: String) = Action.async(parse.form(BenjiForm.createBucketForm)) { request =>
    oos.bucket(bucketName).create(failsIfExists = true).map { _ =>
      Created(s"$bucketName created")
    }.recover {
      case BucketAlreadyExistsException(_) =>
        Ok(s"$bucketName already exists")
    }
  }

  def deleteBucket(bucketName: String) = Action.async(parse.form(BenjiForm.deleteBucketForm)) { request =>
    val delete = oos.bucket(bucketName).delete
    val withIgnore = if (request.body.ignore) delete.ignoreIfNotExists else delete
    val withRecursive = if (request.body.recursive) withIgnore.recursive else withIgnore

    withRecursive.apply().map { _ =>
      NoContent
    }
  }

  def listObjects(bucketName: String) = Action.async(parse.form(BenjiForm.listObjectForm)) { request =>
    val objects = oos.bucket(bucketName).objects
    val withBatchSize = request.body.batchSize.fold(objects)(batchSize => objects.withBatchSize(batchSize))
    withBatchSize.collect[List]().map { objects =>
      Ok(Json.toJson(objects.map(_.name)))
    }
  }

  def getObject(bucketName: String, objectName: String) = Action {
    val data:Source[ByteString, NotUsed] = oos.bucket(bucketName).obj(objectName).get()
    Ok.chunked(data)
  }

  def objectMetadata(bucketName: String, objectName: String) = Action.async {
    val objectRef = oos.bucket(bucketName).obj(objectName)

    objectRef.metadata.map { meta =>
      NoContent.withHeaders(meta.toSeq.flatMap {
        case (name, values) => values.map(name -> _)
      }: _*)
    }.recover {
      case _: ObjectNotFoundException => NotFound
    }
  }

  def createObject(bucketName: String) = Action.async(parse.multipartFormData) { request =>
    val files = request.body.files.map { file =>
      val source = FileIO.fromPath(file.ref.path)
      val uploaded: Future[NotUsed] = source runWith oos.bucket(bucketName).obj(file.filename).put[ByteString]
      uploaded
    }
    if (files.isEmpty) Future.successful(BadRequest("No files to upload"))
    else Future.sequence(files).map { _ => Ok(s"File ${request.body.files.map(_.filename).mkString(",")} uploaded") }
  }

  def deleteObject(bucketName: String, objectName: String) =
    Action.async(parse.form(BenjiForm.deleteObjectForm)) { request =>
      val delete = oos.bucket(bucketName).obj(objectName).delete
      val withIgnore = if (request.body.ignore) delete.ignoreIfNotExists else delete
      withIgnore.apply().map { _ =>
        NoContent
      }
    }
}

object BenjiForm {

  val createBucketForm = Form(
    mapping("checkBefore" -> default(boolean, true))(CreateBucketForm.apply)(CreateBucketForm.unapply))

  val deleteBucketForm = Form(
    mapping(
      "ignore" -> default(boolean, true),
      "recursive" -> default(boolean, false))(DeleteBucketForm.apply)(DeleteBucketForm.unapply))

  val listObjectForm = Form(
    mapping("bacthSize" -> optional(longNumber))(ListObjectForm.apply)(ListObjectForm.unapply))

  val deleteObjectForm = Form(
    mapping("ignore" -> default(boolean, true))(DeleteObjectForm.apply)(DeleteObjectForm.unapply))
}

case class CreateBucketForm(checkBefore: Boolean)

case class DeleteBucketForm(ignore: Boolean, recursive: Boolean)

case class ListObjectForm(batchSize: Option[Long])

case class DeleteObjectForm(ignore: Boolean)