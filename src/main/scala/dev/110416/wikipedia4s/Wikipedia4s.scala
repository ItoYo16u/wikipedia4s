package dev.`110416`.wikipedia4s
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*
import dev.`110416`.wikipedia4s.errors.*
import io.circe.Json
import io.circe.parser._
import sttp.client3.DeserializationException
import sttp.client3.HttpError
import sttp.client3.Response
import sttp.client3.ResponseException
import sttp.client3.SttpBackend
import sttp.client3.SttpClientException
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.basicRequest
import sttp.model.Method
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.client3.Identity
import scala.concurrent.duration.*
import sttp.client3.RequestT

type APIRequest[T] = RequestT[Identity, Either[
  ResponseException[String, io.circe.Error],
  org.openapitools.client.model.ErrorResponse | T
], Any]

type APIResponse[T] = Response[Either[
  ResponseException[String, io.circe.Error],
  org.openapitools.client.model.ErrorResponse | T
]]
trait Wikipedia4s(using ctx: APIContext) {
    implicit val client: org.openapitools.client.api.DefaultApi = org.openapitools.client.api
        .DefaultApi(ctx.uri("http")(ctx.language))


    def query[T <: HasExpectResponseType : BuildRequest](
        q: T
    ): IO[Either[WikiError, q.ResponseType]] = {
        AsyncHttpClientCatsBackend[IO]().flatMap { backend =>
            {
                for {
                    response <- summon[BuildRequest[T]].build(q).send(backend)
                } yield parseResponse(response)
            }
        }
    }

    /// pretty print response in console

    def execute(command: Command): IO[Unit] = {
        command match {
            case Command.Search(query, limit)  => ???
            case Command.Suggest(query, limit) => ???
            case Command.Help                  => ???
        }
    }

    private def parseResponse[T](
        response: APIResponse[T]
    ): Either[WikiError, T] = {
        response.body.leftMap(handleCommonError) match {
            case Right(response: T) =>
                Right(response)
            case Right(response: org.openapitools.client.model.ErrorResponse) =>
                Left(WikiError.ApplicationError(response.error.info))
            case Left(err) => Left(err)
        }
    }

    private def handleCommonError(err: ResponseException[String, io.circe.Error]): WikiError = {
        err match {
            case HttpError(body, statusCode) if statusCode.isClientError =>
                WikiError.InvalidRequestError(body)
            case HttpError(_, _)                     => WikiError.ServerError
            case DeserializationException(body, err) => WikiError.ParseError(body)
        }
    }
}
