package jp.co.sony.csl.dcoes.apis.tools.ccc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post.HttpPostUnitDataReportingImpl;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.mongo_db.MongoDBUnitDataReportingImpl;

/**
 * This Verticle reports unit data to the outside.
 * It is started from {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle.
 * At regular intervals, the verticle obtains unit data from GridMaster and carries out reporting.
 * The actual reporting process has two types below.
 * - If {@code CONFIG.unitDataReporting.type} is {@code http_post} : {@link HttpPostUnitDataReportingImpl}
 * - If {@code CONFIG.unitDataReporting.type} is {@code mongo_db} : {@link MongoDBUnitDataReportingImpl}
 * @author OES Project 
 * ユニットデータを外部に通知する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle から起動される.
 * 一定時間ごとに GridMaster からユニットデータを取得し通知する.
 * 実際の通知処理は以下の 2 つ.
 * - {@code CONFIG.unitDataReporting.type} が {@code http_post} : {@link HttpPostUnitDataReportingImpl}
 * - {@code CONFIG.unitDataReporting.type} が {@code mongo_db} : {@link MongoDBUnitDataReportingImpl}
 * @author OES Project
 */
public class UnitDataReporting extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(UnitDataReporting.class);

	/**
	 * Sets the default value of the reporting cycle in [ms] unit.
	 * The value is {@value}
	 * 通知周期のデフォルト値 [ms].
	 * 値は {@value}
	 */
	private static final Long DEFAULT_UNIT_DATA_REPORTING_PERIOD_MSEC = 30000L;
	/**
	 * Sets the default value of the reporting period.
	 * The value is {@value}
	 * 通知方式のデフォルト値.
	 * 値は {@value}
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_UNIT_DATA_REPORTING_TYPE = new JsonObjectUtil.DefaultString("mongo_db");

	private Impl impl_;
	private boolean enabled_ = false;
	private long unitDataReportingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called during startup.
	 * Gets settings from CONFIG and initializes.
	 * - {@code CONFIG.unitDataReporting.enabled}
	 * - {@code CONFIG.unitDataReporting.type}
	 * Prepares the object to be implemented.
	 * Starts the timer.
	 * @param startPromise {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 * CONFIG から設定を取得し初期化する.
	 * - {@code CONFIG.unitDataReporting.enabled}
	 * - {@code CONFIG.unitDataReporting.type}
	 * 実装オブジェクトを用意する.
	 * タイマを起動する.
	 * @param startPromise {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Promise<Void> startPromise) throws Exception {
		enabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "unitDataReporting", "enabled");
		if (enabled_) {
			if (log.isInfoEnabled()) log.info("unitDataReporting enabled");
			String type = VertxConfig.config.getString(DEFAULT_UNIT_DATA_REPORTING_TYPE, "unitDataReporting", "type");
			try {
				switch (type) {
				case "http_post":
					impl_ = new HttpPostUnitDataReportingImpl(vertx);
					break;
				case "mongo_db":
					impl_ = new MongoDBUnitDataReportingImpl(vertx);
					break;
				}
				if (impl_ == null) {
					startPromise.fail("unknown CONFIG.unitDataReporting.type value : " + type);
					return;
				}
			} catch (Exception e) {
				startPromise.fail(e);
				return;
			}
		} else {
			if (log.isInfoEnabled()) log.info("unitDataReporting disabled");
		}

		if (enabled_) unitDataReportingTimerHandler_(0L);
		if (log.isTraceEnabled()) log.trace("started : {}", deploymentID());
		startPromise.complete();
	}

	/**
	 * Called when stopped.
	 * Sets a flag for stopping the timer.
	 * @throws Exception {@inheritDoc}
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : {}", deploymentID());
	}

	////

	/**
	 * Sets the timer with the default time.
	 * デフォルト時間でタイマをセットする.
	 */
	private void setUnitDataReportingTimer_() {
		Long delay = VertxConfig.config.getLong(DEFAULT_UNIT_DATA_REPORTING_PERIOD_MSEC, "unitDataReporting", "periodMsec");
		setUnitDataReportingTimer_(delay);
	}
	/**
	 * Sets the timer with the time specified by {@code delay}.
	 * @param delay Time set by timer [ms]
	 * {@code delay} で指定した時間でタイマをセットする.
	 * @param delay タイマ設定時間 [ms]
	 */
	private void setUnitDataReportingTimer_(long delay) {
		unitDataReportingTimerId_ = vertx.setTimer(delay, this::unitDataReportingTimerHandler_);
	}
	/**
	 * This process is called by the timer.
	 * @param timerId Timer ID
	 * タイマから呼び出される処理.
	 * @param timerId タイマ ID
	 */
	private void unitDataReportingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != unitDataReportingTimerId_) {
			if (log.isWarnEnabled()) log.warn("illegal timerId : " + timerId + ", unitDataReportingTimerId_ : " + unitDataReportingTimerId_);
			return;
		}
		if (log.isInfoEnabled()) log.info("reporting unit data ...");
		vertx.eventBus().<JsonObject>request(ServiceAddress.GridMaster.unitDatas(), null, rep -> {
			if (rep.succeeded()) {
				JsonObject result = rep.result().body();
				if (result != null) {
					if (log.isInfoEnabled()) log.info("size of unit data : {}", result.size());
					if (!result.isEmpty()) {
						// Ensures that the element is a JsonObject
						// 要素が JsonObject であることを保証する
						JsonObject filtered = new JsonObject();
						for (String aKey : result.fieldNames()) {
							Object aVal = result.getValue(aKey);
							if (aVal instanceof JsonObject) {
								filtered.put(aKey, aVal);
							} else {
								if (log.isWarnEnabled()) log.warn("invalid unitData item : {}", aVal);
							}
						}
						result = filtered;
						if (log.isInfoEnabled()) log.info("size of filtered unit data : {}", result.size());
					}
					if (!result.isEmpty()) {
						// Adds data unit ID
						// データセット ID を追加する
						long id = System.currentTimeMillis();
						if (log.isDebugEnabled()) log.debug("datasetId : {}", id);
						for (String aKey : result.fieldNames()) {
							JsonObject aVal = result.getJsonObject(aKey);
							aVal.put("datasetId", id);
						}
						impl_.report(result, resReport -> {
							if (resReport.succeeded()) {
								// nop
							} else {
								log.error("Communication failed with ServiceCenter", resReport.cause());
							}
							setUnitDataReportingTimer_();
						});
					} else {
						if (log.isWarnEnabled()) log.warn("unit data is empty");
						setUnitDataReportingTimer_();
					}
				} else {
					if (log.isWarnEnabled()) log.warn("unit data is null");
					setUnitDataReportingTimer_();
				}
			} else {
				log.error("Communication failed on EventBus", rep.cause());
				setUnitDataReportingTimer_();
			}
		});
	}

	////

	/**
	 * This is the interface for calling the object to be implemented for the reporting process.
	 * @author OES Project
	 * 通知処理の実装オブジェクトを呼び出すためのインタフェイス.
	 * @author OES Project
	 */
	public interface Impl {
		/**
		 * Sends one Power Sharing information report.
		 * @param unitData Unit data {@link JsonObject}
		 * @param completionHandler The completion handler
		 * 融通情報を一つ通知する.
		 * @param unitData ユニットデータ {@link JsonObject}
		 * @param completionHandler the completion handler
		 */
		void report(JsonObject unitData, Handler<AsyncResult<Void>> completionHandler);
	}

}
