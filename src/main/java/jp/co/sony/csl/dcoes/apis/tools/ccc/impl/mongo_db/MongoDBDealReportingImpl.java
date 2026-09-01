package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.mongo_db;

import java.time.ZonedDateTime;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.DealReporting;

/**
 * Implements direct storing of Power Sharing information in MongoDB.
 * Used in {@link DealReporting}.
 * @author OES Project
 * MongoDB に対して融通情報を直接保存する実装.
 * {@link DealReporting} で使用される.
 * @author OES Project
 */
public class MongoDBDealReportingImpl implements DealReporting.Impl {
	private static final Logger log = LoggerFactory.getLogger(MongoDBDealReportingImpl.class);

	private static final JsonObjectUtil.DefaultString DEFAULT_HOST = JsonObjectUtil.defaultString("localhost");
	private static final Integer DEFAULT_PORT = Integer.valueOf(27017);
	private static final Boolean DEFAULT_SSL = Boolean.FALSE;
	private static final Boolean DEFAULT_SSL_TRUST_ALL = Boolean.FALSE;

	@SuppressWarnings("unused") private Vertx vertx_;
	private static MongoClient client_ = null;
	private static String collection_ = null;

	/**
	 * Creates instance.
	 * Gets settings from CONFIG and initializes.
	 * - CONFIG.dealReporting.host : Connection destination host name [{@link String}].
	 *                               Default : localhost.
	 * - CONFIG.dealReporting.port : Connection destination port [{@link String}].
	 *                               Default : 27017.
	 * - CONFIG.dealReporting.ssl : SSL flag [{@link Boolean}].
	 *                              Default : false.
	 * - CONFIG.dealReporting.sslTrustAll : OK flag [{@link Boolean}] for any SSL.
	 *                                      Default : false.
	 * - CONFIG.dealReporting.database : Database name [{@link String}].
	 *                                   Required.
	 * - CONFIG.dealReporting.collection : Collection name [{@link String}].
	 *                                     Required.
	 * @param vertx vertx object
	 * インスタンスを作成する.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.dealReporting.host : 接続先ホスト名 [{@link String}].
	 *                               デフォルト : localhost.
	 * - CONFIG.dealReporting.port : 接続先ポート [{@link Integer}].
	 *                               デフォルト : 27017.
	 * - CONFIG.dealReporting.ssl : SSL フラグ [{@link Boolean}].
	 *                              デフォルト : false.
	 * - CONFIG.dealReporting.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}].
	 *                                      デフォルト : false.
	 * - CONFIG.dealReporting.database : データベース名 [{@link String}].
	 *                                   必須.
	 * - CONFIG.dealReporting.collection : コレクション名 [{@link String}].
	 *                                     必須.
	 * @param vertx vertx オブジェクト
	 */
	public MongoDBDealReportingImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString(DEFAULT_HOST, "dealReporting", "host");
		Integer port = VertxConfig.config.getInteger(DEFAULT_PORT, "dealReporting", "port");
		Boolean ssl = VertxConfig.config.getBoolean(DEFAULT_SSL, "dealReporting", "ssl");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(DEFAULT_SSL_TRUST_ALL, "dealReporting", "sslTrustAll");
		String database = VertxConfig.config.getString("dealReporting", "database");
		JsonObject config = new JsonObject().put("host", host).put("port", port).put("ssl", ssl).put("db_name", database);
		if (ssl) config.put("trustAll", sslTrustAll);
		client_ = MongoClient.createShared(vertx, config);
		collection_ = VertxConfig.config.getString("dealReporting", "collection");
		if (log.isInfoEnabled()) log.info("host : {}", host);
		if (log.isInfoEnabled()) log.info("port : {}", port);
		if (log.isInfoEnabled()) log.info("ssl : {}", ssl);
		if (ssl) if (log.isInfoEnabled()) log.info("sslTrustAll : {}", sslTrustAll);
		if (log.isInfoEnabled()) log.info("database : {}", database);
		if (log.isInfoEnabled()) log.info("collection : {}", collection_);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		saveOne_(deal, completionHandler);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonArray deals, Handler<AsyncResult<Void>> completionHandler) {
		saveMulti_(deals.copy(), completionHandler);
	}

	private void saveMulti_(JsonArray deals, Handler<AsyncResult<Void>> completionHandler) {
		if (deals.isEmpty()) {
			completionHandler.handle(Future.succeededFuture());
		} else {
			JsonObject aDeal = (JsonObject) deals.remove(0);
			saveOne_(aDeal, res -> {
				saveMulti_(deals, completionHandler);
			});
		}
	}
	private static final FindOptions FIND_OPTIONS_ = new FindOptions();
	private static final UpdateOptions UPDATE_OPTIONS_ = new UpdateOptions().setUpsert(true);//.setReturningNewDocument(true);
	private void saveOne_(JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		String dealId = Deal.dealId(deal);
		if (dealId == null) {
			completionHandler.handle(Future.failedFuture("no dealId exists"));
		} else {
			convertDateTimeField_(deal);
			JsonObject query = new JsonObject().put("dealId", dealId);
			client_.findOneAndReplaceWithOptions(collection_, query, deal, FIND_OPTIONS_, UPDATE_OPTIONS_, res -> {
				if (res.succeeded()) {
//					if (log.isDebugEnabled()) log.debug("findOneAndReplaceWithOptions succeeded : {}", res.result());
					completionHandler.handle(Future.succeededFuture());
				} else {
					log.error("Communication failed with MongoDB", res.cause());
					log.error("query : {}", query);
					log.error("deal : {}", deal);
					completionHandler.handle(Future.failedFuture(res.cause()));
				}
			});
		}
	}

	/**
	 * Converts all attributes ending with "DateTime" to ISO format because they should be datetime strings in the standard format for the APIS program.
	 * @param obj DEAL object to convert.
	 * "DateTime" で終わる属性はすべて APIS プログラムの標準フォーマットの日時文字列のはずなので ISO フォーマットに変換してあれする.
	 * @param obj 変換対象 DEAL オブジェクト
	 */
	private void convertDateTimeField_(JsonObject obj) {
		for (String aKey : obj.fieldNames()) {
			Object aVal = obj.getValue(aKey);
			if (aVal instanceof String) {
				if (aKey.endsWith("DateTime") || aKey.equals("dateTime")) {
					ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime((String) aVal);
					String iso8601 = DateTimeUtil.toISO8601OffsetString(zdt);
					obj.put(aKey, new JsonObject().put("$date", iso8601));
				}
			} else if (aVal instanceof JsonObject) {
				convertDateTimeField_((JsonObject) aVal);
			} else if (aVal instanceof JsonArray) {
				for (Object anObj : (JsonArray) aVal) {
					if (anObj instanceof JsonObject) {
						convertDateTimeField_((JsonObject) anObj);
					}
				}
			}
		}
	}

}
