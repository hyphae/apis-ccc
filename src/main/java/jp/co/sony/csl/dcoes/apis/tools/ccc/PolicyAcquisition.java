package jp.co.sony.csl.dcoes.apis.tools.ccc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post.HttpPostPolicyAcquisitionImpl;

/**
 * This Verticle gets POLICY from the outside.
 * It is started from {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle.
 * Gets POLICY from the outside as needed in response to internal requests.
 * Actual acquistion processing is implemented in {@link HttpPostPolicyAcquisitionImpl}.
 * @author OES Project
 * 外部から POLICY を取得する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle から起動される.
 * 内部からの要求に応じて外部から取得する.
 * 実際の取得処理は {@link HttpPostPolicyAcquisitionImpl} で実装.
 * @author OES Project
 */
public class PolicyAcquisition extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(PolicyAcquisition.class);

	private Impl impl_;
	private boolean enabled_ = false;

	/**
	 * Called during startup.
	 * Gets settings from CONFIG and initializes.
	 * - {@code CONFIG.policyAcquisition.enabled}
	 * Prepares the object to be implemented.
	 * Starts {@link io.vertx.core.eventbus.EventBus} service.
	 * @param startPromise {@inheritDoc}
	 * @throws Exception {@inheritDoc}
 	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - {@code CONFIG.policyAcquisition.enabled}
	 * 実装オブジェクトを用意する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startPromise {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Promise<Void> startPromise) throws Exception {
		enabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "policyAcquisition", "enabled");
		if (enabled_) {
			if (log.isInfoEnabled()) log.info("policyAcquisition enabled");
			impl_ = new HttpPostPolicyAcquisitionImpl(vertx);
		} else {
			if (log.isInfoEnabled()) log.info("policyAcquisition disabled");
		}

		startPolicyService_(resPolicy -> {
			if (resPolicy.succeeded()) {
				if (log.isTraceEnabled()) log.trace("started : {}", deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resPolicy.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : {}", deploymentID());
	}

	////

	/**
	 * Starts {@link io.vertx.core.eventbus.EventBus} service.
	 * Address : {@link ServiceAddress.ControlCenterClient#policy()}
	 * Scope : Global
	 * Processing : Gets POLICY from Service Center.
	 * 　　   account, password, unitId are required.
	 * Message body : None
	 * Message header :
	 * 　　　　　　　　   - {@code "account"} : Account
	 * 　　　　　　　　   - {@code "password"} : Password
	 * 　　　　　　　　   - {@code "unitId"} : Unit ID
	 * Responses : Acquired POLICY information [{@link JsonObject}].
	 * 　　　　　   Fail if error occurs.
	 * @param completionHandler The completion handler
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.ControlCenterClient#policy()}
	 * 範囲 : グローバル
	 * 処理 : Service Center から POLICY を取得する.
	 * 　　   account, password, unitId が必要.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "account"} : アカウント
	 * 　　　　　　　　   - {@code "password"} : パスワード
	 * 　　　　　　　　   - {@code "unitId"} : ユニット ID
	 * レスポンス : 取得した POLICY 情報 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startPolicyService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.ControlCenterClient.policy(), req -> {
			if (impl_ != null) {
				String account = req.headers().get("account");
				String password = req.headers().get("password");
				String unitId = req.headers().get("unitId");
				impl_.acquireCurrent(account, password, unitId, resAcquire -> {
					if (resAcquire.succeeded()) {
						JsonObject result = resAcquire.result();
						req.reply(result);
					} else {
						log.error("Communication failed with ServiceCenter", resAcquire.cause());
						req.fail(-1, resAcquire.cause().getMessage());
					}
				});
			} else {
				req.reply(null);
			}
		}).completionHandler(completionHandler);
	}

	////

	/**
	 * This is the interface for calling the object to be implemented for the acquisition process.
	 * @author OES Project
	 * 取得処理の実装オブジェクトを呼び出すためのインタフェイス.
	 * @author OES Project
	 */
	public interface Impl {
		/**
		 * Gets POLICY.
		 * To be received by completionHandler's {@link AsyncResult#result()}.
		 * @param account Verified account for Service Center
		 * @param password Verified password for Service Center 
		 * @param unitId Unit ID
		 * @param completionHandler The completion handler
		 * POLICY を取得する.
		 * completionHandler の {@link AsyncResult#result()} で受け取る.
		 * @param account Service Center の認証アカウント
		 * @param password Service Center の認証パスワード
		 * @param unitId ユニット ID
		 * @param completionHandler the completion handler
		 */
		void acquireCurrent(String account, String password, String unitId, Handler<AsyncResult<JsonObject>> completionHandler);
	}

}
