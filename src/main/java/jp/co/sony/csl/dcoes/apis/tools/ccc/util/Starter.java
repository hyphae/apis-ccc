// SPDX-License-Identifier: Apache-2.0

package jp.co.sony.csl.dcoes.apis.tools.ccc.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter;
import jp.co.sony.csl.dcoes.apis.tools.ccc.DealReporting;
import jp.co.sony.csl.dcoes.apis.tools.ccc.PolicyAcquisition;
import jp.co.sony.csl.dcoes.apis.tools.ccc.ScenarioAcquisition;
import jp.co.sony.csl.dcoes.apis.tools.ccc.UnitDataReporting;

/**
 * These are the main Verticles of apis-ccc.
 * They are specified in maven-shade-plugin's {@literal <Main-Verticle>} in pom.xml.
 * Starts the following Verticles.
 * - {@link DealReporting} : Verticle that reports Power Sharing information to the outside
 * - {@link UnitDataReporting} : Verticle that reports unit data to the outside
 * - {@link ScenarioAcquisition} : Verticle that gets SCENARIO from the outside 
 * - {@link PolicyAcquisition} : Verticle that gets POLICY from the outside
 * @author OES Project
 * apis-ccc の親玉 Verticle.
 * pom.xml の maven-shade-plugin の {@literal <Main-Verticle>} で指定してある.
 * 以下の Verticle を起動する.
 * - {@link DealReporting} : 融通情報を外部に通知する Verticle
 * - {@link UnitDataReporting} : ユニットデータを外部に通知する Verticle
 * - {@link ScenarioAcquisition} : 外部から SCENARIO を取得する Verticle
 * - {@link PolicyAcquisition} : 外部から POLICY を取得する Verticle
 * @author OES Project
 */
public class Starter extends AbstractStarter {

	/**
	 * Called from {@link AbstractStarter#start(Future)} during startup.
	 * 起動時に {@link AbstractStarter#start(Future)} から呼び出される.
	 */
	@Override protected void doStart(Handler<AsyncResult<Void>> completionHandler) {
		vertx.deployVerticle(new DealReporting(), resDealReporting -> {
			if (resDealReporting.succeeded()) {
				vertx.deployVerticle(new UnitDataReporting(), resUnitDataReporting -> {
					if (resUnitDataReporting.succeeded()) {
						vertx.deployVerticle(new ScenarioAcquisition(), resScenarioAcquisition -> {
							if (resScenarioAcquisition.succeeded()) {
								vertx.deployVerticle(new PolicyAcquisition(), resPolicyAcquisition -> {
									if (resPolicyAcquisition.succeeded()) {
										completionHandler.handle(Future.succeededFuture());
									} else {
										completionHandler.handle(Future.failedFuture(resPolicyAcquisition.cause()));
									}
								});
							} else {
								completionHandler.handle(Future.failedFuture(resScenarioAcquisition.cause()));
							}
						});
					} else {
						completionHandler.handle(Future.failedFuture(resUnitDataReporting.cause()));
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resDealReporting.cause()));
			}
		});
	}

}
