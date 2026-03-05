// SPDX-License-Identifier: Apache-2.0

package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.ZonedDateTime;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.UnitDataReporting;

/**
 * Implements reporting of unit data via HTTP POST for web service.
 * Used in {@link UnitDataReporting} 
 * @author OES Project
 * ウェブサービスに対して HTTP POST でユニットデータを通知する実装. 
 * {@link UnitDataReporting} で使用される.
 * @author OES Project
 */
public class HttpPostUnitDataReportingImpl implements UnitDataReporting.Impl {
	private static final Logger log = LoggerFactory.getLogger(HttpPostUnitDataReportingImpl.class);

	/**
	 * This is the default HTTP connection timeout value [ms].
	 * The value is {@value}.
	 * HTTP 接続のタイムアウトのデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;

	private Vertx vertx_;
	private HttpClient client_;
	private String uri_;

	/**
	 * Creates instance.
	 * Gets settings from CONFIG and initializes.
	 * - CONFIG.unitDataReporting.host : Connection destination host name [{@link String}]
	 * - CONFIG.unitDataReporting.ssl : SSL flag  [{@link Boolean}]
	 * - CONFIG.unitDataReporting.sslTrustAll : OK flag [{@link Boolean}] for any SSL
	 * - CONFIG.unitDataReporting.port : Connection destination port [{@link Integer}].
	 *                                   If there are no settings, 443 for SSL, 80 for all else.
	 * - CONFIG.unitDataReporting.uri : Connection destination URI [{@link String}]
	 * @param vertx vertx object
	 * インスタンスを作成する.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.unitDataReporting.host : 接続先ホスト名 [{@link String}]
	 * - CONFIG.unitDataReporting.ssl : SSL フラグ [{@link Boolean}]
	 * - CONFIG.unitDataReporting.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}]
	 * - CONFIG.unitDataReporting.port : 接続先ポート [{@link Integer}].
	 *                                   設定がない場合 SSL なら 443, そうでなければ 80.
	 * - CONFIG.unitDataReporting.uri : 接続先 URI [{@link String}]
	 * @param vertx vertx オブジェクト
	 */
	public HttpPostUnitDataReportingImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString("unitDataReporting", "host");
		Boolean isSsl = VertxConfig.config.getBoolean(false, "unitDataReporting", "ssl");
		Integer port = (isSsl) ? VertxConfig.config.getInteger(443, "unitDataReporting", "port") : VertxConfig.config.getInteger(80, "unitDataReporting", "port");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(false, "unitDataReporting", "sslTrustAll");
		uri_ = VertxConfig.config.getString("unitDataReporting", "uri");
		if (log.isInfoEnabled()) log.info("host : " + host);
		if (log.isInfoEnabled()) log.info("port : " + port);
		if (isSsl) if (log.isInfoEnabled()) log.info("sslTrustAll : " + sslTrustAll);
		if (log.isInfoEnabled()) log.info("uri : " + uri_);
		client_ = vertx_.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port).setSsl(isSsl).setTrustAll(sslTrustAll));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		convertDateTimeField_(unitData);
		JsonArray unitDataAsArray = toArray_(unitData);
		Buffer body = Buffer.buffer(unitDataAsArray.encode());
		if (log.isDebugEnabled()) log.debug("body : " + body);
		new Poster_(body).execute_(completionHandler);
	}

	private JsonArray toArray_(JsonObject obj) {
		JsonArray result = new JsonArray();
		for (String aKey : obj.fieldNames()) {
			result.add(obj.getJsonObject(aKey));
		}
		return result;
	}

	/**
	 * Converts all attributes ending with "time" to ISO format because they should be datetime strings in the standard format for the APIS program.
	 * @param obj DEAL object to convert
	 * "time" で終わる属性はすべて APIS プログラムの標準フォーマットの日時文字列のはずなので ISO フォーマットに変換する.
	 * @param obj 変換対象 UNITDATA オブジェクト
	 */
	private void convertDateTimeField_(JsonObject obj) {
		for (String aKey : obj.fieldNames()) {
			Object aVal = obj.getValue(aKey);
			if (aVal instanceof String) {
				if (aKey.endsWith("time")) {
					ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime((String) aVal);
					obj.put(aKey, DateTimeUtil.toISO8601OffsetString(zdt));
				}
			} else if (aVal instanceof JsonObject) {
				convertDateTimeField_((JsonObject) aVal);
			}
		}
	}

	////

	private class Poster_ {
		private Buffer body_;
		private boolean completed_ = false;
		private Poster_(Buffer body) {
			body_ = body;
		}
		/**
		 * Executes HTTP POST processing.
		 * (Maybe because of poor implementation) The result may be returned twice, so this is blocked here.
		 * HTTP POST 処理実行.
		 * ( 実装がまずいのか ) 二度結果が返ってくることがあるためここでブロックする.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			post_(r -> {
				if (!completed_) {
					completed_ = true;
					completionHandler.handle(r);
				} else {
					if (log.isWarnEnabled()) log.warn("post_() result returned more than once : " + r);
				}
			});
		}
		/**
 		* NOTE: The method {@code HttpClient.post(String, Handler<HttpClientResponse>)} used below is deprecated
 		* in newer versions of Vert.x. However, this project currently targets Vert.x 3.5.3, where this method is
 		* the supported and appropriate way to perform simple HTTP POST requests.
 		*
 		* It is acceptable to continue using this method for now because:
 		* - The newer, preferred API ({@code request(HttpMethod, ...)} or {@code WebClient}) was introduced in later
 		*   Vert.x versions (3.6+ for request-based API, 3.5.4+ for WebClient).
 		* - Upgrading Vert.x is not feasible at this time due to compatibility constraints across the project.
 		*
 		* Refactoring to the modern API should be planned once the project is upgraded to a newer Vert.x version
 		* (3.6+ or 4.x) to ensure forward compatibility and eliminate deprecation warnings.
 		*/
		@SuppressWarnings("deprecation")
		private void post_(Handler<AsyncResult<Void>> completionHandler) {
			Long requestTimeoutMsec = VertxConfig.config.getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "unitDataReporting", "requestTimeoutMsec");
			client_.post(uri_, resPost -> {
				if (log.isDebugEnabled()) log.debug("status : " + resPost.statusCode());
				if (resPost.statusCode() == 200) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					resPost.bodyHandler(error -> {
						completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + error));
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + t));
					});
				}
			}).setTimeout(requestTimeoutMsec).exceptionHandler(t -> {
				completionHandler.handle(Future.failedFuture(t));
			}).putHeader("content-type", "application/json").putHeader("content-length", String.valueOf(body_.length())).write(body_).end();
		}
	}

}
