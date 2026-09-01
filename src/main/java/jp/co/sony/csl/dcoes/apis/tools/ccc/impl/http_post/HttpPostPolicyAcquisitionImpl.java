package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.util.StringUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.PolicyAcquisition;

/**
 * Implements acquisition of POLICY via HTTP POST for web service. 
 * Used in {@link PolicyAcquisition}.
 * @author OES Project
 */
public class HttpPostPolicyAcquisitionImpl implements PolicyAcquisition.Impl {
	private static final Logger log = LoggerFactory.getLogger(HttpPostPolicyAcquisitionImpl.class);

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
	 * - CONFIG.policyAcquisition.host : Connection destination host name [{@link String}]
	 * - CONFIG.policyAcquisition.ssl : SSL flag [{@link Boolean}]
	 * - CONFIG.policyAcquisition.sslTrustAll : OK flag [{@link Boolean}] for any SSL
	 * - CONFIG.policyAcquisition.port : Destination connection port [{@link Integer}].
	 *                                   If there are no settings, 443 for SSL, 80 for all else.
	 * - CONFIG.policyAcquisition.uri : Connection destination URI [{@link String}]
	 * @param vertx vertx object
	 * インスタンスを作成する.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.policyAcquisition.host : 接続先ホスト名 [{@link String}]
	 * - CONFIG.policyAcquisition.ssl : SSL フラグ [{@link Boolean}]
	 * - CONFIG.policyAcquisition.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}]
	 * - CONFIG.policyAcquisition.port : 接続先ポート [{@link Integer}].
	 *                                   設定がない場合 SSL なら 443, そうでなければ 80.
	 * - CONFIG.policyAcquisition.uri : 接続先 URI [{@link String}]
	 * @param vertx vertx オブジェクト
	 */
	public HttpPostPolicyAcquisitionImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString("policyAcquisition", "host");
		Boolean isSsl = VertxConfig.config.getBoolean(false, "policyAcquisition", "ssl");
		Integer port = (isSsl) ? VertxConfig.config.getInteger(443, "policyAcquisition", "port") : VertxConfig.config.getInteger(80, "policyAcquisition", "port");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(false, "policyAcquisition", "sslTrustAll");
		uri_ = VertxConfig.config.getString("policyAcquisition", "uri");
		if (log.isInfoEnabled()) log.info("host : {}", host);
		if (log.isInfoEnabled()) log.info("port : {}", port);
		if (isSsl) if (log.isInfoEnabled()) log.info("sslTrustAll : {}", sslTrustAll);
		if (log.isInfoEnabled()) log.info("uri : {}", uri_);
		client_ = vertx_.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port).setSsl(isSsl).setTrustAll(sslTrustAll));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void acquireCurrent(String account, String password, String unitId, Handler<AsyncResult<JsonObject>> completionHandler) {
		Buffer body = Buffer.buffer();
		body.appendString("account=").appendString(StringUtil.urlEncode(account));
		body.appendString("&password=").appendString(StringUtil.urlEncode(password));
		body.appendString("&communityId=").appendString(StringUtil.urlEncode(VertxConfig.communityId()));
		body.appendString("&clusterId=").appendString(StringUtil.urlEncode(VertxConfig.clusterId()));
		body.appendString("&unitId=").appendString(StringUtil.urlEncode(unitId));
		body.appendString("&isMD5Password=true");
		if (log.isDebugEnabled()) log.debug("body : {}", body);
		new Poster_(body).execute_(completionHandler);
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
		 * @param completionHandler The completion handler
		 * HTTP POST 処理実行.
		 * ( 実装がまずいのか ) 二度結果が返ってくることがあるためここでブロックする.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<JsonObject>> completionHandler) {
			post_(r -> {
				if (!completed_) {
					completed_ = true;
					completionHandler.handle(r);
				} else {
					if (log.isWarnEnabled()) log.warn("post_() result returned more than once : {}", r);
				}
			});
		}

		private void post_(Handler<AsyncResult<JsonObject>> completionHandler) {
            Long requestTimeoutMsec = VertxConfig.config.getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "policyAcquisition", "requestTimeoutMsec");
            
            // 1. Initiate the asynchronous request
            client_.request(io.vertx.core.http.HttpMethod.POST, uri_).onComplete(reqResult -> {
                if (reqResult.failed()) {
                    completionHandler.handle(Future.failedFuture(reqResult.cause()));
                    return;
                }

                io.vertx.core.http.HttpClientRequest req = reqResult.result();

                // Configure request properties
                req.setTimeout(requestTimeoutMsec);
                req.putHeader("content-type", "application/x-www-form-urlencoded");
                req.putHeader("content-length", String.valueOf(body_.length()));

                // Handle network/connection exceptions
                req.exceptionHandler(t -> {
                    completionHandler.handle(Future.failedFuture(t));
                });

                // 2. Send the body and capture the response
                req.send(body_, resPostResult -> {
                    if (resPostResult.failed()) {
                        completionHandler.handle(Future.failedFuture(resPostResult.cause()));
                        return;
                    }

                    io.vertx.core.http.HttpClientResponse resPost = resPostResult.result();

                    if (log.isDebugEnabled()) log.debug("status : {}", resPost.statusCode());

                    if (resPost.statusCode() == 200) {
                        resPost.bodyHandler(buffer -> {
                            String resp = String.valueOf(buffer);
                            if (0 < resp.length()) {
                                try {
                                    JsonObject result = new JsonObject(resp);
                                    if (log.isDebugEnabled()) log.debug("result : {}", result);
                                    completionHandler.handle(Future.succeededFuture(result));
                                } catch (Exception e) {
                                    // Safeguard for JSON parsing issues
                                    completionHandler.handle(Future.failedFuture(e));
                                }
                            } else {
                                if (log.isDebugEnabled()) log.debug("result : null");
                                completionHandler.handle(Future.succeededFuture());
                            }
                        }).exceptionHandler(t -> {
                            completionHandler.handle(Future.failedFuture(t));
                        });
                    } else {
                        resPost.bodyHandler(error -> {
                            completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + error));
                        }).exceptionHandler(t -> {
                            completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + t));
                        });
                    }
                });
            });
        }
	}

}
