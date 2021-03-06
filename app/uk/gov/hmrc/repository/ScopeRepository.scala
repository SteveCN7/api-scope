/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.repository

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.models.Scope
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.metrics.{API, Metrics, PlayMetrics}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private object ScopeFormats {
  implicit val objectIdFormats = ReactiveMongoFormats.objectIdFormats
  implicit val scopeFormat = Json.format[Scope]
}

@Singleton
class ScopeRepository @Inject()(mongo: ReactiveMongoComponent)
  extends ReactiveRepository[Scope, BSONObjectID]("scope", mongo.mongoConnector.db,
    ScopeFormats.scopeFormat, ReactiveMongoFormats.objectIdFormats) {

  lazy val metrics: Metrics = PlayMetrics

  ensureIndex("key", "keyIndex")

  def save(scope: Scope) : Future[Scope] = metrics.record(API("mongo-scope-save")) {
    collection.find(Json.obj("key" -> scope.key)).one[BSONDocument].flatMap {
      case Some(document) => collection.update(selector = BSONDocument("_id" -> document.get("_id")), update = scope)
      case None => collection.save(scope)
    }.map(_ => scope)
  }

  private def ensureIndex(field: String, indexName: String, isUnique: Boolean = true, isBackground: Boolean = true): Future[Boolean] = {
    def createIndex = Index(
      key = Seq(field -> IndexType.Ascending),
      name = Some(indexName),
      unique = isUnique,
      background = isBackground
    )

    collection.indexesManager.ensure(createIndex)
  }

  def fetch(key: String): Future[Option[Scope]] = metrics.record(API("mongo-scope-fetch")) {
    collection.find(Json.obj("key" -> key)).one[Scope] map {
      case Some(s) => Some(s)
      case None =>
        Logger.info(s"The scope $key doesn't exist")
        None
    }
  }

  def fetchAll(): Future[Seq[Scope]] = metrics.record(API("mongo-scope-fetch-all")) {
    collection.find(Json.obj()).cursor[Scope].collect[Seq]()
  }

}
