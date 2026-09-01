package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.mongo_db;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.UnitDataReporting;

/**
 * Implements direct storing of unit data in MongoDB.
 * Used in {@link UnitDataReporting}.
 * @author OES Project
 * MongoDB に対してユニットデータを直接保存する実装.
 * {@link UnitDataReporting} で使用される.
 * @author OES Project
 */
public class MongoDBUnitDataReportingImpl implements UnitDataReporting.Impl {
	private static final Logger log = LoggerFactory.getLogger(MongoDBUnitDataReportingImpl.class);

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
	 * - CONFIG.unitDataReporting.host : Connection destination host name [{@link String}].
	 *                                   Default : localhost.
	 * - CONFIG.unitDataReporting.port : Connection destination port [{@link Integer}].
	 *                                   Default : 27017.
	 * - CONFIG.unitDataReporting.ssl : SSL flag [{@link Boolean}].
	 *                                  Default : false.
	 * - CONFIG.unitDataReporting.sslTrustAll : OK flag [{@link Boolean}] for any SSL.
	 *                                          Default : false.
	 * - CONFIG.unitDataReporting.database : Database name [{@link String}].
	 *                                       Required.
	 * - CONFIG.unitDataReporting.collection : Collection name [{@link String}].
	 *                                         Required.
	 * @param vertx vertx object
	 * インスタンスを作成する.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.unitDataReporting.host : 接続先ホスト名 [{@link String}].
	 *                                   デフォルト : localhost.
	 * - CONFIG.unitDataReporting.port : 接続先ポート [{@link Integer}].
	 *                                   デフォルト : 27017.
	 * - CONFIG.unitDataReporting.ssl : SSL フラグ [{@link Boolean}].
	 *                                  デフォルト : false.
	 * - CONFIG.unitDataReporting.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}].
	 *                                          デフォルト : false.
	 * - CONFIG.unitDataReporting.database : データベース名 [{@link String}].
	 *                                       必須.
	 * - CONFIG.unitDataReporting.collection : コレクション名 [{@link String}].
	 *                                         必須.
	 * @param vertx vertx オブジェクト
	 */
	public MongoDBUnitDataReportingImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString(DEFAULT_HOST, "unitDataReporting", "host");
		Integer port = VertxConfig.config.getInteger(DEFAULT_PORT, "unitDataReporting", "port");
		Boolean ssl = VertxConfig.config.getBoolean(DEFAULT_SSL, "unitDataReporting", "ssl");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(DEFAULT_SSL_TRUST_ALL, "unitDataReporting", "sslTrustAll");
		String database = VertxConfig.config.getString("unitDataReporting", "database");
		JsonObject config = new JsonObject().put("host", host).put("port", port).put("ssl", ssl).put("db_name", database);
		if (ssl) config.put("trustAll", sslTrustAll);
		client_ = MongoClient.createShared(vertx, config);
		collection_ = VertxConfig.config.getString("unitDataReporting", "collection");
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
	@Override public void report(JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		List<JsonObject> list = new ArrayList<>(unitData.size());
		for (String aKey : unitData.fieldNames()) {
			list.add(unitData.getJsonObject(aKey));
		}
		saveMulti_(list, completionHandler);
	}

	private void saveMulti_(List<JsonObject> list, Handler<AsyncResult<Void>> completionHandler) {
		if (list.isEmpty()) {
			completionHandler.handle(Future.succeededFuture());
		} else {
			JsonObject aData = list.remove(0);
			saveOne_(aData, res -> {
				saveMulti_(list, completionHandler);
			});
		}
	}
	private void saveOne_(JsonObject data, Handler<AsyncResult<Void>> completionHandler) {
		convertDateTimeField_(data);
		client_.insert(collection_, data, res -> {
			if (res.succeeded()) {
//				if (log.isDebugEnabled()) log.debug("insert succeeded : {}", res.result());
				completionHandler.handle(Future.succeededFuture());
			} else {
				log.error("Communication failed with MongoDB", res.cause());
				log.error("unitData : {}", data);
				completionHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/**
	 * Converts all attributes ending with "time" to ISO format because they should be datetime strings in the standard format for the APIS program.
	 * @param obj 変換対象 DEAL object to convert.
	 * "time" で終わる属性はすべて APIS プログラムの標準フォーマットの日時文字列のはずなので ISO フォーマットに変換してあれする.
	 * @param obj 変換対象 DEAL オブジェクト
	 */
	private void convertDateTimeField_(JsonObject obj) {
		for (String aKey : obj.fieldNames()) {
			Object aVal = obj.getValue(aKey);
			if (aVal instanceof String) {
				if (aKey.endsWith("time")) {
					ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime((String) aVal);
					String iso8601 = DateTimeUtil.toISO8601OffsetString(zdt);
					obj.put(aKey, new JsonObject().put("$date", iso8601));
				}
			} else if (aVal instanceof JsonObject) {
				convertDateTimeField_((JsonObject) aVal);
			}
		}
	}

}
