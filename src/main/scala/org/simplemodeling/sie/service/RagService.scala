package org.simplemodeling.sie.service

import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine
import io.circe.*
import cats.effect.unsafe.implicits.global
import cats.effect.IO

/*
 * @since   Nov. 20, 2025
 *          Nov. 25, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
case class RagResult(
  concepts: Json,
  passages: Json
) derives Encoder

class RagService(
  fuseki: FusekiClient,
  chroma: ChromaClient,
  embedding: EmbeddingEngine
):
  def runIO(query: String): IO[RagResult] =
    val conceptsIO =
      fuseki
        .searchConceptsIO(query)
        .handleError(e => Json.obj("error" -> Json.fromString(e.toString)))

    val embedIO =
      if embedding.mode.toString.toLowerCase == "none" then
        IO.pure(None)
      else
        embedding.embed(List(query))
          .handleError(_ => None)

    def passagesIO(vecOpt: Option[Array[Float]]): IO[Json] =
      IO {
        chroma
          .search("simplemodeling", query, 5, vecOpt)
          .getOrElse(Json.arr())
      }.handleError(e =>
        Json.obj("error" -> Json.fromString(e.toString))
      )

    for
      concepts <- conceptsIO
      vecList  <- embedIO
      vec =
        vecList match
          case None => None
          case Some(Nil) => None
          case Some(xs) =>
            xs.headOption match
              case None => None
              case Some(arr) =>
                if arr.isEmpty then None else Some(arr)
      passages <- passagesIO(vec)
    yield RagResult(concepts, passages)

  def run(query: String): RagResult =
    runIO(query).unsafeRunSync()
