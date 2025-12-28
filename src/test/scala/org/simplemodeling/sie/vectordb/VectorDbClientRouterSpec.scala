package org.simplemodeling.sie.vectordb

import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class VectorDbClientRouterSpec extends AnyWordSpec with Matchers {

  private final class TestWriteClient extends VectorDbWriteClient {
    var existsCalls = 0
    var upsertCalls = 0

    override def collectionExists(name: String): IO[Boolean] =
      IO { existsCalls += 1; true }

    override def createCollection(name: String): IO[Unit] = IO.unit

    override def upsert(collection: String, records: List[VectorRecord]): IO[Unit] =
      IO { upsertCalls += 1; () }
  }

  private final class TestReadClient extends VectorDbReadClient {
    var queryCalls = 0

    override def query(collection: String, vector: Vector, topK: Int): IO[List[VectorMatch]] =
      IO { queryCalls += 1; Nil }
  }

  "VectorDbClientRouter.readWrite" should {

    "delegate read and write operations" in { pending }
  }
}
