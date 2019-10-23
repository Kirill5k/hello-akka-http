package io.kirill.helloakkahttp.highlevel

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class ServerSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {
  import ServerSpec._

  "A digital library controller" should {
    "return all books" in {
      Get("/api/books") ~> booksRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }

    "return book by id" in {
      Get("/api/books/1") ~> booksRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(1, "JRR Tolkien", "The Lord of the Rings"))
      }
    }

    "return book by id as query param" in {
      Get("/api/books?id=2") ~> booksRoute ~> check {
        status shouldBe StatusCodes.OK

        val strictEntityFuture = response.entity.toStrict(2 seconds)
        val strictEntity = Await.result(strictEntityFuture, 2 seconds)

        strictEntity.contentType shouldBe ContentTypes.`application/json`

        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "GRR Martin", "A Song of Ice and Fire"))
      }
    }

    "insert a new book" in {
      val newBook = Book(5, "Steven Pressfield", "The War of Art")
      Post("/api/books", newBook) ~> booksRoute ~> check {
        status shouldBe StatusCodes.Created
//        assert(books.contains(newBook))
        books should contain(newBook)
      }
    }

    "not accept other methods" in {
      Delete("/api/books/1") ~> booksRoute ~> check {
        rejections should not be empty

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }
  }
}


trait BookJsonProtocol extends DefaultJsonProtocol {
  import ServerSpec._

  implicit val bookFormat = jsonFormat3(Book)
}

object ServerSpec extends BookJsonProtocol with SprayJsonSupport {

  case class Book(id: Int, author: String, title: String)

  var books = List(
    Book(1, "JRR Tolkien", "The Lord of the Rings"),
    Book(2, "GRR Martin", "A Song of Ice and Fire"),
    Book(3, "Tony Robbins", "Awaken the Gian Within"),
    Book(4, "Harper Lee", "To Kill a Mockingbird")
  )

  val booksRoute = pathPrefix("api" / "books") {
    get {
      (path(IntNumber) | parameter("id".as[Int])) { id =>
        val book = books.find(_.id == id)
        complete(book)
      } ~
      pathEndOrSingleSlash {
        complete(books)
      }
    } ~
    post {
      entity(as[Book]) { book =>
        books = books :+ book
        complete(StatusCodes.Created)
      } ~
      complete(StatusCodes.BadRequest)
    }
  }
}
